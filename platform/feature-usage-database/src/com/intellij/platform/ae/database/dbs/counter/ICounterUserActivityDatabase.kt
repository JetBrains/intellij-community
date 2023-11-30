package com.intellij.platform.ae.database.dbs.counter

import com.intellij.platform.ae.database.activities.DatabaseBackedCounterUserActivity
import java.time.Instant

interface ICounterUserActivityDatabase {
  suspend fun submit(activity: DatabaseBackedCounterUserActivity, diff: Int)
}

internal interface IInternalCounterUserActivityDatabase {
  suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant)
  fun executeBeforeConnectionClosed(action: suspend () -> Unit)
}

interface IReadOnlyCounterUserActivityDatabase {
  suspend fun getActivitySum(activity: DatabaseBackedCounterUserActivity, from: Instant?, until: Instant?): Int
}