// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("SqlResolve")

package com.intellij.platform.ae.database.dbs.counter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.ae.database.IdService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.ae.database.activities.DatabaseBackedCounterUserActivity
import com.intellij.platform.ae.database.dbs.IUserActivityDatabaseLayer
import com.intellij.platform.ae.database.dbs.SqliteInitializedDatabase
import com.intellij.platform.ae.database.utils.InstantUtils
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.sqlite.ObjectBinderFactory
import java.time.Instant

/**
 * Database for storing and retrieving counter-based events.
 *
 * All events are rounded to a minute, seconds and milliseconds are always 0
 */
class CounterUserActivityDatabase(cs: CoroutineScope, private val database: SqliteInitializedDatabase) : ICounterUserActivityDatabase,
                                                                                                         IUserActivityDatabaseLayer,
                                                                                                         IReadOnlyCounterUserActivityDatabase,
                                                                                                         IInternalCounterUserActivityDatabase {
  private val throttler = CounterUserActivityDatabaseThrottler(cs, this, runBackgroundUpdater = !ApplicationManager.getApplication().isUnitTestMode)

  /**
   * Retrieves the activity for a user based on the provided activity ID and time range.
   * Answers the question 'How many times did activity happen in given timeframe?'
   */
  override suspend fun getActivitySum(activity: DatabaseBackedCounterUserActivity, from: Instant?, until: Instant?): Int {

    val nnFrom = InstantUtils.formatForDatabase(from ?: InstantUtils.SomeTimeAgo)
    val nnUntil = InstantUtils.formatForDatabase(until ?: InstantUtils.NowButABitLater)

    return execute {
      val getActivityStatement = database.connection.prepareStatement(
        "SELECT sum(diff) FROM counterUserActivity WHERE activity_id = ? AND created_at >= ? AND created_at <= ?",
        ObjectBinderFactory.create3<String, String, String>()
      )
      throttler.commitChanges()

      getActivityStatement.binder.bind(activity.id, nnFrom, nnUntil)
      getActivityStatement.selectInt() ?: 0
    }
  }

  /**
   * Main entry point for submitting new event update
   *
   * This method doesn't submit to database, but first submits to throttler which
   */
  override suspend fun submit(activity: DatabaseBackedCounterUserActivity, diff: Int) {
    thisLogger().info("${activity.id} = $diff")
    throttler.submit(activity, diff)
  }

  /**
   * Writes event directly to database. Very internal API!
   */
  override suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant) {
    execute {
      val updateActivityStatement = database.connection.prepareStatement(
        "INSERT INTO counterUserActivity (activity_id, diff, created_at, ide_id) VALUES (?, ?, ?, ?)",
        ObjectBinderFactory.create4<String, Int, String, Int>()
      )
      updateActivityStatement.binder.bind(activity.id, diff, InstantUtils.formatForDatabase(instant), IdService.getInstance().getDatabaseId(database))
      updateActivityStatement.executeUpdate()
    }
  }

  override fun invokeOnDatabaseDeath(action: suspend () -> Unit) {
    database.invokeBeforeDatabaseDeath(action)
  }

  override suspend fun <T> execute(action: suspend () -> T): T {
    return database.execute {
      action()
    }
  }
}
