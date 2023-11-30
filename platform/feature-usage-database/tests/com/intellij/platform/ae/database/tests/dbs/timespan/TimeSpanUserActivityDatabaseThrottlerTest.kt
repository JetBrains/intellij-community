package com.intellij.platform.ae.database.tests.dbs.timespan

import com.intellij.platform.ae.database.activities.DatabaseBackedTimeSpanUserActivity
import com.intellij.platform.ae.database.dbs.timespan.IInternalTimeSpanUserActivityDatabase
import com.intellij.platform.ae.database.dbs.timespan.TimeSpanUserActivityDatabaseManualKind
import com.intellij.platform.ae.database.dbs.timespan.TimeSpanUserActivityDatabaseThrottler
import com.intellij.platform.ae.database.utils.InstantUtils
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.junit.Assert
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toKotlinDuration

class TimeSpanUserActivityDatabaseThrottlerTest : BasePlatformTestCase() {
  private val updatePause = 4.seconds
  private val eventTtl = Duration.ofSeconds(2L)

  private fun runCounterUserActivityTest(
    submit: suspend (TimeSpanUserActivityDatabaseThrottler, DatabaseBackedTimeSpanUserActivity) -> Unit,
    asserts: (
      activity: DatabaseBackedTimeSpanUserActivity,
      startedAt: Instant,
      endedAt: Instant,
      isFinished: Boolean,
      lock: Mutex
    ) -> Unit) {
    val myActivity = object : DatabaseBackedTimeSpanUserActivity() {
      override val id: String get() = "testActivity"
    }

    val lock = Mutex()

    val onDatabaseDeath = mutableListOf<suspend () -> Unit>()

    val fakeDatabase = object : IInternalTimeSpanUserActivityDatabase {
      override suspend fun endEventInternal(databaseId: Int?,
                                            activity: DatabaseBackedTimeSpanUserActivity,
                                            startedAt: Instant,
                                            endedAt: Instant,
                                            isFinished: Boolean,
                                            extra: Map<String, String>?): Int {
        asserts(activity, startedAt, endedAt, isFinished, lock)
        return 0
      }

      override fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
        error("Called executeBeforeConnectionClosed which doesnt work in current test")
      }
    }

    timeoutRunBlocking {
      val throttlerCoroutine = launch {
        TimeSpanUserActivityDatabaseThrottler(this, fakeDatabase, updatePause, eventTtl).apply {
          submit(this, myActivity)
        }
      }

      val submissionTime = Instant.now()

      lock.withLock {
        println("Took ${Duration.between(submissionTime, Instant.now()).seconds} seconds to submit event")
        for (task in onDatabaseDeath) {
          task()
        }
        throttlerCoroutine.cancel()
      }
    }
  }

  fun testOnePeriodicNoUpdate() {
    var submittedAt: Instant = InstantUtils.Now

    val submit: suspend (TimeSpanUserActivityDatabaseThrottler, DatabaseBackedTimeSpanUserActivity) -> Unit = { thr, activity ->
      submittedAt = thr.submitPeriodic(activity, "testing", false, null)!!
    }

    val verify: (DatabaseBackedTimeSpanUserActivity, Instant, Instant, Boolean, Mutex) -> Unit = { activity, start, end, isFinished, lock ->
      Assert.assertEquals(submittedAt, start)
      Assert.assertEquals(submittedAt + Duration.ofSeconds(eventTtl.seconds / 2), end)
      Assert.assertTrue(isFinished)
      lock.unlock()
    }

    runCounterUserActivityTest(submit, verify)
  }

  fun testOnePeriodicWithUpdate() {
    var submittedAt = InstantUtils.Now

    val submit: suspend (TimeSpanUserActivityDatabaseThrottler, DatabaseBackedTimeSpanUserActivity) -> Unit = { thr, activity ->
      submittedAt = thr.submitPeriodic(activity, "testing", false, null)!!
      delay(1.seconds)
      thr.submitPeriodic(activity, "testing", false, null)
    }

    val verify: (DatabaseBackedTimeSpanUserActivity, Instant, Instant, Boolean, Mutex) -> Unit = { activity, start, end, isFinished, lock ->
      Assert.assertEquals(submittedAt, start)
      Assert.assertNotEquals(submittedAt + Duration.ofSeconds(eventTtl.seconds / 2), end)
      Assert.assertTrue(isFinished)
      lock.unlock()
    }

    runCounterUserActivityTest(submit, verify)
  }

  fun testResubmitPeriodicAsManualFails() {
    val submit: suspend (TimeSpanUserActivityDatabaseThrottler, DatabaseBackedTimeSpanUserActivity) -> Unit = { thr, activity ->
      val periodicSubmitTime = thr.submitPeriodic(activity, "testing", false, null)
      Assert.assertNotNull(periodicSubmitTime)

      val manualSubmitTime = thr.submitManual(
        activity,
        "testing",
        TimeSpanUserActivityDatabaseManualKind.Start,
        canBeStale = false,
        moment = InstantUtils.Now,
        extra = null,
      )
      Assert.assertNull(manualSubmitTime)
    }

    val verify: (DatabaseBackedTimeSpanUserActivity, Instant, Instant, Boolean, Mutex) -> Unit = { _, _, _, _, _ -> }

    runCounterUserActivityTest(submit, verify)
  }

  fun testResubmitManualAsPeriodicFails() {
    val submit: suspend (TimeSpanUserActivityDatabaseThrottler, DatabaseBackedTimeSpanUserActivity) -> Unit = { thr, activity ->
      val periodicSubmitTime = thr.submitPeriodic(activity, "testing", false, null)
      Assert.assertNotNull(periodicSubmitTime)

      val manualSubmitTime = thr.submitManual(
        activity,
        "testing",
        TimeSpanUserActivityDatabaseManualKind.Start,
        canBeStale = false,
        moment = InstantUtils.Now,
        extra = null,
      )
      Assert.assertNull(manualSubmitTime)
    }

    val verify: (DatabaseBackedTimeSpanUserActivity, Instant, Instant, Boolean, Mutex) -> Unit = { _, _, _, _, _ -> }
    runCounterUserActivityTest(submit, verify)
  }

  fun testShowdown() {
    val myActivity1 = object : DatabaseBackedTimeSpanUserActivity() {
      override val id: String get() = "testActivity1"
    }
    val myActivity2 = object : DatabaseBackedTimeSpanUserActivity() {
      override val id: String get() = "testActivity2"
    }

    val onDatabaseDeath = mutableListOf<suspend () -> Unit>()

    timeoutRunBlocking {
      val endedEvents = mutableListOf<String>()

      val fakeDatabase = object : IInternalTimeSpanUserActivityDatabase {
        override suspend fun endEventInternal(databaseId: Int?,
                                              activity: DatabaseBackedTimeSpanUserActivity,
                                              startedAt: Instant,
                                              endedAt: Instant,
                                              isFinished: Boolean,
                                              extra: Map<String, String>?): Int {
          endedEvents.add(activity.id)
          return 0
        }

        override fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
          onDatabaseDeath.add(action)
        }
      }

      val submissionLock = Mutex(true)
      val throttlerCoroutine = launch {
        TimeSpanUserActivityDatabaseThrottler(this, fakeDatabase, 10.minutes, Duration.ofNanos(1)).apply {
          submitPeriodic(myActivity1, myActivity1.id, false, null)
          submitManual(myActivity2, myActivity2.id, TimeSpanUserActivityDatabaseManualKind.Start, true, InstantUtils.Now, null)
          submitManual(myActivity2, myActivity2.id, TimeSpanUserActivityDatabaseManualKind.End, true, InstantUtils.NowButABitLater, null)
          submissionLock.unlock()
        }
      }

      submissionLock.lock()

      for (task in onDatabaseDeath) {
        task()
      }

      throttlerCoroutine.cancel()

      Assert.assertTrue(endedEvents.contains(myActivity1.id))
      Assert.assertTrue(endedEvents.contains(myActivity2.id))
    }
  }

  fun testUnfinishedSaved() {
    val myActivity1 = object : DatabaseBackedTimeSpanUserActivity() {
      override val id: String get() = "testActivity1"
    }
    val myActivity2 = object : DatabaseBackedTimeSpanUserActivity() {
      override val id: String get() = "testActivity2"
    }

    val eventTtl = Duration.ofMillis(30)
    val updateInterval = 10.milliseconds

    val onDatabaseDeath = mutableListOf<suspend () -> Unit>()

    timeoutRunBlocking {
      val endedEvents = mutableListOf<String>()
      val savedEvents = mutableMapOf<String, Int>()

      val fakeDatabase = object : IInternalTimeSpanUserActivityDatabase {
        var idSeq = 0

        private fun nextId(): Int {
          idSeq += 1
          return idSeq
        }

        override suspend fun endEventInternal(databaseId: Int?,
                                              activity: DatabaseBackedTimeSpanUserActivity,
                                              startedAt: Instant,
                                              endedAt: Instant,
                                              isFinished: Boolean,
                                              extra: Map<String, String>?): Int {
          val itemId = databaseId ?: nextId()
          if (isFinished) {
            endedEvents.add(activity.id)
          } else {
            savedEvents[activity.id] = itemId
          }
          return itemId
        }

        override fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
          onDatabaseDeath.add(action)
        }
      }

      val firstSubmissionLock = Mutex(true)
      val secondSubmissionLock = Mutex(true)
      val assertionLock = Mutex(true)

      val throttlerCoroutine = launch {
        TimeSpanUserActivityDatabaseThrottler(
          this,
          fakeDatabase,
          updateInterval,
          eventTtl,
        ).apply {
          submitPeriodic(myActivity1, myActivity1.id, false, null)
          submitManual(myActivity2, myActivity2.id, TimeSpanUserActivityDatabaseManualKind.Start, true, InstantUtils.Now, null)
          firstSubmissionLock.unlock()
          assertionLock.lock()
          submitManual(myActivity2, myActivity2.id, TimeSpanUserActivityDatabaseManualKind.End, true, InstantUtils.Now, null)
          secondSubmissionLock.unlock()
        }
      }

      firstSubmissionLock.lock()

      // Wait to commit changes once
      delay(updateInterval.times(1.1))
      Assert.assertTrue(savedEvents.contains(myActivity1.id))
      Assert.assertTrue(savedEvents.contains(myActivity2.id))
      Assert.assertTrue(endedEvents.isEmpty())

      val activity1DbId = savedEvents[myActivity1.id]
      val activity2DbId = savedEvents[myActivity2.id]

      assertionLock.unlock()

      // Wait for second submission
      secondSubmissionLock.lock()

      // Wait for event ttl to expire
      delay(eventTtl.toKotlinDuration())

      // Assert both events ended
      Assert.assertTrue(endedEvents.contains(myActivity1.id))
      Assert.assertTrue(endedEvents.contains(myActivity2.id))

      // Assert id wasn't changed (called with the same dbId)
      Assert.assertEquals(activity1DbId, savedEvents[myActivity1.id])
      Assert.assertEquals(activity2DbId, savedEvents[myActivity2.id])

      throttlerCoroutine.cancel()
    }
  }
}