package dao

import javax.inject.{Inject, Singleton}
import models.Models.FullMix
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

trait FullMixComponent {
    self: HasDatabaseConfigProvider[JdbcProfile] =>

    import profile.api._

    class FullMixTable(tag: Tag) extends Table[FullMix](tag, "FULL_MIX") {
        def id = column[String]("MIX_ID")

        def round = column[Int]("ROUND")

        def createdTime = column[Long]("CREATED_TIME")

        def halfMixBoxId = column[String]("HALF_MIX_BOX_ID")

        def fullMixBoxId = column[String]("FULL_MIX_BOX_ID")

        def * = (id, round, createdTime, halfMixBoxId, fullMixBoxId) <> (FullMix.tupled, FullMix.unapply)

        def pk = primaryKey("pk_FULL_MIX", (id, round))
    }

    class FullMixArchivedTable(tag: Tag) extends Table[(String, Int, Long, String, String, String)](tag, "FULL_MIX_ARCHIVED") {
        def id = column[String]("MIX_ID")

        def round = column[Int]("ROUND")

        def createdTime = column[Long]("CREATED_TIME")

        def halfMixBoxId = column[String]("HALF_MIX_BOX_ID")

        def fullMixBoxId = column[String]("FULL_MIX_BOX_ID")

        def reason = column[String]("REASON")

        def * = (id, round, createdTime, halfMixBoxId, fullMixBoxId, reason)

        def pk = primaryKey("pk_FULL_MIX_ARCHIVED", (id, round))
    }

}

@Singleton()
class FullMixDAO @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
    extends FullMixComponent with MixStateComponent
        with HasDatabaseConfigProvider[JdbcProfile] {

    import profile.api._

    val fullMix = TableQuery[FullMixTable]

    val mixes = TableQuery[MixStateTable]

    val fullMixArchive = TableQuery[FullMixArchivedTable]

    /**
     * inserts a mix state into MIX_STATE table
     *
     * @param mix FullMix
     */
    def insert(mix: FullMix): Future[Unit] = db.run(fullMix += mix).map(_ => ())

    /**
     * deletes all of mixes
     *
     */
    def clear: Future[Unit] = db.run(fullMix.delete).map(_ => ())

    /**
     * selects fullBoxId by mixId and round (joining in mixState)
     *
     * @param mixId String
     */
    def selectFullBoxIdByMixId(mixId: String): Future[Option[String]] = {
        val query = for {
            (req, state) <- fullMix join mixes
            if req.id === mixId && state.id === mixId && req.round === state.round
        } yield req.fullMixBoxId
        db.run(query.result.headOption)
    }

    /**
     * selects fullBoxId by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def selectBoxId(mixId: String, round: Int): Future[Option[String]] = {
        val query = for {
            mix <- fullMix if mix.id === mixId && mix.round === round
        } yield mix.fullMixBoxId
        db.run(query.result.headOption)
    }

    /**
     * checks if the halfMixBoxId exists in table or not
     *
     * @param halfMixBoxId String
     */
    def existsByBoxId(halfMixBoxId: String): Future[Boolean] = db.run(fullMix.filter(mix => mix.halfMixBoxId === halfMixBoxId).exists.result)

    /**
     * selects fullMix by mixId and round
     *
     * @param mixId String
     * @param round Int
     */
    def selectOption(mixId: String, round: Int): Future[Option[FullMix]] = db.run(fullMix.filter(mix => mix.id === mixId && mix.round === round).result.headOption)

    /**
     * selects fullMix by mixId
     *
     * @param mixId String
     */
    def boxIdByMixId(mixId: String): Future[Option[String]] = db.run(fullMix.filter(mix => mix.id === mixId).map(_.fullMixBoxId).result.headOption)

    /**
     * delete fullMix by mixId
     *
     * @param mixId String
     */
    def deleteWithArchive(mixId: String): Future[Unit] = db.run(DBIO.seq(
        fullMix.filter(mix => mix.id === mixId).delete,
        fullMixArchive.filter(mix => mix.id === mixId).delete
    ))

    /**
     * deletes future fullMix by mixId
     *
     * @param mixId String
     * @param round Int
     */
    def deleteFutureRounds(mixId: String, round: Int): Future[Unit] = db.run(fullMix.filter(mix => mix.id === mixId && mix.round > round).delete).map(_ => ())

    /**
     * updates full mix by mixId
     *
     * @param new_mix FullMix
     */
    def updateById(new_mix: FullMix)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        fullMix.filter(mix => mix.id === new_mix.mixId && mix.round === new_mix.round).delete,
        fullMix += new_mix,
        fullMixArchive += (new_mix.mixId, new_mix.round, new_mix.createdTime, new_mix.halfMixBoxId, new_mix.fullMixBoxId, insertReason)
    ))

    /**
     * inserts fullMix box into FullMix and FullMixArchived tables
     *
     * @param new_fullMix FullMix
     */
    def insertFullMix(new_fullMix: FullMix)(implicit insertReason: String): Future[Unit] = db.run(DBIO.seq(
        fullMix += new_fullMix,
        fullMixArchive += (new_fullMix.mixId, new_fullMix.round, new_fullMix.createdTime, new_fullMix.halfMixBoxId, new_fullMix.fullMixBoxId, insertReason),
    ))
}
