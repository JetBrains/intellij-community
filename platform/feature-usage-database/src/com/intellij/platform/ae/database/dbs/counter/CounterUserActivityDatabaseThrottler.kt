package com.intellij.platform.ae.database.dbs.counter

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.ae.database.activities.DatabaseBackedCounterUserActivity
import com.intellij.platform.ae.database.utils.InstantUtils
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = logger<CounterUserActivityDatabaseThrottler>()

private data class CounterUserActivityHits(
  val firstOccurance: Instant,
  var lastOccurance: Instant,
  var count: Int
)

/**
 * Collects events and posts it as a "one big event" to a database to avoid intensive write operations
 */
internal class CounterUserActivityDatabaseThrottler(private val cs: CoroutineScope,
                                                    private val database: IInternalCounterUserActivityDatabase,
                                                    private val updatePause: Duration = 2.minutes,
                                                    runBackgroundUpdater: Boolean = true) {
  private val events = HashMap<DatabaseBackedCounterUserActivity, CounterUserActivityHits>()
  private val eventsLock = Mutex()

  init {
    if (runBackgroundUpdater) {
      cs.launch {
        while (isActive) {
          logger.trace("Updater was (re)started")
          delay(updatePause)
          commitChanges()
          logger.trace("Updater finished")
        }
      }
    }

    database.executeBeforeConnectionClosed {
      commitChanges()
    }
  }

  suspend fun submit(activity: DatabaseBackedCounterUserActivity, newValue: Int) {
    eventsLock.withLock {
      val now = InstantUtils.Now
      val ev = events.getOrElse(activity) {
        CounterUserActivityHits(now, now, 0)
      }

      ev.lastOccurance = now
      ev.count += newValue

      events[activity] = ev
    }
  }

  suspend fun commitChanges() {
    eventsLock.withLock {
      if (events.isNotEmpty()) {
        events
          .filter { it.value.count != 0 }
          .apply { logger.info("Started committing changes ($size)") }
          .forEach {
            database.submitDirect(it.key, it.value.count, it.value.lastOccurance)
          }
        events.clear()
      }
    }
  }
}