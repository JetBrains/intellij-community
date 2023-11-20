// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.platform.ae.database.dbs.timespan

import com.intellij.platform.ae.database.IdService
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.activities.WritableDatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.dbs.IInternalUserActivityDatabaseLayer
import com.intellij.platform.ae.database.dbs.ISqliteBackedDatabaseLayer
import com.intellij.platform.ae.database.dbs.IUserActivityDatabaseLayer
import com.intellij.platform.ae.database.dbs.SqliteInitializedDatabase
import com.intellij.platform.ae.database.formatString
import com.intellij.platform.ae.database.models.TimeSpan
import com.intellij.platform.ae.database.utils.BooleanUtils
import com.intellij.platform.ae.database.utils.InstantUtils
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.sqlite.ObjectBinder
import org.jetbrains.sqlite.ObjectBinderFactory
import java.time.Instant

enum class TimeSpanUserActivityDatabaseManualKind {
  Start,
  End
}

private val logger = logger<TimeSpanUserActivityDatabase>()

class TimeSpanUserActivityDatabase(cs: CoroutineScope, val database: SqliteInitializedDatabase) : IUserActivityDatabaseLayer,
                                                                                                          IReadOnlyTimeSpanUserActivityDatabase,
                                                                                                          ITimeSpanUserActivityDatabase,
                                                                                                          IInternalTimeSpanUserActivityDatabase,
                                                                                                          ISqliteBackedDatabaseLayer {
  private val throttler = TimeSpanUserActivityDatabaseThrottler(cs, this)

  override suspend fun getLongestActivity(activity: DatabaseBackedTimeSpanUserActivity, from: Instant?, until: Instant?): TimeSpan {
    return execute {
      throttler.commitChanges(false)

      val longestActivityStatement = database.connection.prepareStatement(
        """SELECT activity_id, started_at, ended_at FROM timespanUserActivity 
      |WHERE activity_id = ? AND (julianday(started_at) >= julianday(?) AND julianday(?) <= julianday(ended_at)) 
      |ORDER BY (julianday(ended_at) - julianday(started_at)) DESC LIMIT ?""".trimMargin(),
        ObjectBinderFactory.create4<String, String, String, Int>()
      )

      longestActivityStatement.binder.bind(activity.id, InstantUtils.formatForDatabase(from ?: InstantUtils.SomeTimeAgo), InstantUtils.formatForDatabase(until ?: InstantUtils.NowButABitLater), 1)

      longestActivityStatement.executeQuery().let {
        val activityId = it.getString(0) ?: error("Required column activityId was not found")
        val startedAt = it.getString(1) ?: error("Required column startedAt was not found")
        val endedAt = it.getString(2) ?: error("Required column endedAt was not found")
        TimeSpan(activity, activityId, InstantUtils.fromString(startedAt), InstantUtils.fromString(endedAt))
      }
    }
  }

  /**
   * Some events may know when they end, so it's more preferable to use this method
   *
   * @param activity
   * @param kind
   * @param canBeStale if true, database will end event when coroutine scope (=application) dies
   */
  override suspend fun submitManual(activity: DatabaseBackedTimeSpanUserActivity,
                                    id: String,
                                    kind: TimeSpanUserActivityDatabaseManualKind,
                                    canBeStale: Boolean,
                                    extra: Map<String, String>?,
                                    moment: Instant?) {
    throttler.submitManual(activity, id, kind, canBeStale, moment, extra)
  }

  override suspend fun endEventInternal(
    databaseId: Int?,
    activity: DatabaseBackedTimeSpanUserActivity,
    startedAt: Instant,
    endedAt: Instant,
    isFinished: Boolean,
    extra: Map<String, String>?,
  ): Int {
    val dbStartedAt = InstantUtils.formatForDatabase(startedAt)
    val dbEndedAt = InstantUtils.formatForDatabase(endedAt)
    val dbIsFinished = BooleanUtils.formatForDatabase(isFinished)

    return execute {
      if (databaseId != null) {
        val endEventUpdateStatement = database.connection.prepareStatement(
          """
      UPDATE timespanUserActivity 
      SET ended_at = ?,
          is_finished = ?
      WHERE id = ?
    """.trimIndent(),
          ObjectBinderFactory.create3<String, Int, Int>(),
        )
        endEventUpdateStatement.binder.bind(dbEndedAt, dbIsFinished, databaseId)
        endEventUpdateStatement.executeUpdate()
        // Return the current ID
        databaseId
      }
      else {
        val endEventInsertStatement = database.connection.prepareStatement(
          """
      INSERT INTO timespanUserActivity (
        activity_id,
        ide_id,
        started_at,
        ended_at,
        is_finished,
        extra
      )
      VALUES (?, ?, ?, ?, ?, ?)
      RETURNING id;
    """.trimIndent(),
          ObjectBinderFactory.create6<String, Int, String, String, Int, String?>(),
        )

        val extraString = extra?.let { formatString(it) }
        endEventInsertStatement.binder.bind(
          activity.id,
          IdService.getInstance().getDatabaseId(database),
          dbStartedAt,
          dbEndedAt,
          dbIsFinished,
          extraString,
        )

        // Return inserted ID
        endEventInsertStatement
          .executeQuery()
          .getInt(0)
      }
    }
  }

  /**
   * Use this method if you don't know when your event will end. Feel free to call this method as much as you want.
   * Event is considered to be finished if no updates were submitted in +-2 minutes
   *
   * @param activity
   * @param id
   * @param extra the first submitted [extra] will be used
   */
  override suspend fun submitPeriodicEvent(activity: WritableDatabaseBackedTimeSpanUserActivity, id: String, extra: Map<String, String>?) {
    throttler.submitPeriodic(activity, id, activity.canBeStale, extra)
  }

  override fun invokeOnDatabaseDeath(action: suspend () -> Unit) {
    database.invokeBeforeDatabaseDeath(action)
  }

  override suspend fun <T> execute(action: suspend () -> T): T {
    return database.execute {
      action()
    }
  }

  override val tableName: String = "timespanUserActivity"
}

interface IReadOnlyTimeSpanUserActivityDatabase {
  suspend fun getLongestActivity(activity: DatabaseBackedTimeSpanUserActivity, from: Instant?, until: Instant?): TimeSpan
}

interface ITimeSpanUserActivityDatabase : IReadOnlyTimeSpanUserActivityDatabase {
  suspend fun submitManual(activity: DatabaseBackedTimeSpanUserActivity,
                           id: String,
                           kind: TimeSpanUserActivityDatabaseManualKind,
                           canBeStale: Boolean,
                           extra: Map<String, String>? = null,
                           moment: Instant?)
  suspend fun submitPeriodicEvent(activity: WritableDatabaseBackedTimeSpanUserActivity, id: String, extra: Map<String, String>? = null)
}

internal interface IInternalTimeSpanUserActivityDatabase : IInternalUserActivityDatabaseLayer {
  suspend fun endEventInternal(
    databaseId: Int?,
    activity: DatabaseBackedTimeSpanUserActivity,
    startedAt: Instant,
    endedAt: Instant,
    isFinished: Boolean,
    extra: Map<String, String>? = null,
  ): Int
}