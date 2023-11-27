package com.intellij.platform.ae.database.dbs.counter

import com.intellij.platform.ae.database.activities.DatabaseBackedCounterUserActivity
import com.intellij.platform.ae.database.utils.InstantUtils
import java.time.Instant

interface ICounterUserActivityDatabase {
  suspend fun submit(activity: DatabaseBackedCounterUserActivity, diff: Int, eventTime: Instant = InstantUtils.Now)
}

internal interface IInternalCounterUserActivityDatabase {
  suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant)
  fun executeBeforeConnectionClosed(action: suspend () -> Unit)
}

interface IReadOnlyCounterUserActivityDatabase {
  suspend fun getActivitySum(activity: DatabaseBackedCounterUserActivity, from: Instant?, until: Instant?): Int
}