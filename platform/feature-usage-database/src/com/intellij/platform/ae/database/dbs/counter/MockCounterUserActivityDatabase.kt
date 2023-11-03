package com.intellij.platform.ae.database.dbs.counter

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.activities.DatabaseBackedCounterUserActivity
import com.intellij.platform.ae.database.dbs.IUserActivityDatabaseLayer
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import java.time.Instant

private val logger = logger<MockCounterUserActivityDatabase>()

class MockCounterUserActivityDatabase(private val cs: CoroutineScope) : ICounterUserActivityDatabase, IUserActivityDatabaseLayer, IInternalCounterUserActivityDatabase {
  override suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant) {
    logger.info("Tried to submitDirect ${activity.id} with diff $diff at $instant, but running mock")
  }

  override fun invokeOnDatabaseDeath(action: suspend () -> Unit) {
    cs.awaitCancellationAndInvoke {
      action()
    }
  }

  override suspend fun <T> execute(action: suspend () -> T): T {
    return action()
  }

  override suspend fun submit(activity: DatabaseBackedCounterUserActivity, diff: Int) {
    logger.info("Tried to submit ${activity.id} with diff $diff, but running mock")
  }
}