package com.intellij.platform.ae.database.dbs.timespan

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.activities.WritableDatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.dbs.IUserActivityDatabaseLayer
import com.intellij.platform.ae.database.models.TimeSpan
import kotlinx.coroutines.CoroutineScope
import java.time.Instant

private val logger = logger<MockTimeSpanUserActivityDatabase>()

class MockTimeSpanUserActivityDatabase(private val cs: CoroutineScope) : IUserActivityDatabaseLayer,
                                                                         IReadOnlyTimeSpanUserActivityDatabase,
                                                                         ITimeSpanUserActivityDatabase,
                                                                         IInternalTimeSpanUserActivityDatabase {
  override suspend fun endEventInternal(databaseId: Int?,
                                        activity: DatabaseBackedTimeSpanUserActivity,
                                        startedAt: Instant,
                                        endedAt: Instant,
                                        isFinished: Boolean,
                                        extra: Map<String, String>?): Int {
    logger.info("Called endEventInterval on ${activity.id} (dbId=$databaseId), startedAt=$startedAt, endedAt=$endedAt, isFinished=$isFinished")
    return 0
  }

  override fun invokeOnDatabaseDeath(action: suspend () -> Unit) {}

  override suspend fun <T> execute(action: suspend () -> T) = action()

  override suspend fun submitManual(activity: DatabaseBackedTimeSpanUserActivity,
                                    id: String,
                                    kind: TimeSpanUserActivityDatabaseManualKind,
                                    canBeStale: Boolean,
                                    extra: Map<String, String>?,
                                    moment: Instant?) {
    logger.info("Called submitManual on ${activity.id}, id=$id, kind=$kind, canBeStale=$canBeStale, moment=$moment")
  }

  override suspend fun submitPeriodicEvent(activity: WritableDatabaseBackedTimeSpanUserActivity, id: String, extra: Map<String, String>?) {
    logger.info("Called submitPeriodicEvent on ${activity.id}, id=$id")
  }

  override suspend fun getLongestActivity(activity: DatabaseBackedTimeSpanUserActivity, from: Instant?, until: Instant?): TimeSpan {
    throw IllegalStateException("Getter methods are not implemented in mock")
  }
}