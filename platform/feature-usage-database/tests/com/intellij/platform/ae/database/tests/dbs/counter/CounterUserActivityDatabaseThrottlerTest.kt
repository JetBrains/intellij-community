package com.intellij.platform.ae.database.tests.dbs.counter

import com.intellij.platform.ae.database.activities.DatabaseBackedCounterUserActivity
import com.intellij.platform.ae.database.dbs.counter.CounterUserActivityDatabaseThrottler
import com.intellij.platform.ae.database.dbs.counter.IInternalCounterUserActivityDatabase
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CounterUserActivityDatabaseThrottlerTest : BasePlatformTestCase() {
  private fun runCounterUserActivityTest(flushImmediately: Boolean, submit: suspend (CounterUserActivityDatabaseThrottler, DatabaseBackedCounterUserActivity) -> Unit, asserts: (diff: Int, instant: Instant, lock: Mutex) -> Unit) {
    val myActivity = object : DatabaseBackedCounterUserActivity() {
      override val id: String get() = "testActivity"
    }

    val lock = Mutex()

    val onDatabaseDeath = mutableListOf<suspend () -> Unit>()
    val fakeDatabase = object : IInternalCounterUserActivityDatabase {
      override suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant) {
        Assert.assertTrue(myActivity === activity)
        asserts(diff, instant, lock)
      }

      override fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
        onDatabaseDeath.add(action)
      }
    }

    timeoutRunBlocking {
      val throttlerCoroutine = launch {
        CounterUserActivityDatabaseThrottler(this, fakeDatabase, 2.seconds).apply {
          submit(this, myActivity)
          /*
          // TODO: if this is uncommented, tests work faster, but different from how it works in prod
          if (flushImmediately) {
            commitChanges()
          }
          */
        }
      }

      val submissionTime = Instant.now()
      // The first lock() call locks, the second one awaits
      lock.lock()
      lock.lock()
      println("Took ${Duration.between(submissionTime, Instant.now()).seconds} seconds to submit event")
      for (task in onDatabaseDeath) {
        task()
      }
      throttlerCoroutine.cancel()
    }
  }

  fun testOneEvent() {
    val newValue = 13
    val submit: suspend (CounterUserActivityDatabaseThrottler, DatabaseBackedCounterUserActivity) -> Unit = { thr, act ->
      thr.submit(act, newValue)
    }
    val verify: (Int, Instant, Mutex) -> Unit = { diff, instant, lock ->
      Assert.assertEquals(newValue, diff)
      lock.unlock()
    }
    runCounterUserActivityTest(true, submit, verify)
  }

  fun testSeveralEvents() {
    val firstValue = 13
    val secondValue = 1989
    val result = firstValue+secondValue

    val submit: suspend (CounterUserActivityDatabaseThrottler, DatabaseBackedCounterUserActivity) -> Unit = { thr, act ->
      thr.submit(act, firstValue)
      thr.submit(act, secondValue)
    }
    val verify: (Int, Instant, Mutex) -> Unit = { diff, instant, lock ->
      Assert.assertEquals(result, diff)
      lock.unlock()
    }
    runCounterUserActivityTest(true, submit, verify)
  }

  fun testSeveralEventsNegative() {
    val firstValue = 13
    val secondValue = -1989
    val result = firstValue+secondValue

    val submit: suspend (CounterUserActivityDatabaseThrottler, DatabaseBackedCounterUserActivity) -> Unit = { thr, act ->
      thr.submit(act, firstValue)
      thr.submit(act, secondValue)
    }
    val verify: (Int, Instant, Mutex) -> Unit = { diff, instant, lock ->
      Assert.assertEquals(result, diff)
      lock.unlock()
    }
    runCounterUserActivityTest(true, submit, verify)
  }

  fun testSeveralValuesSeparate() {
    val firstValue = 13
    val secondValue = -1989

    val submit: suspend (CounterUserActivityDatabaseThrottler, DatabaseBackedCounterUserActivity) -> Unit = { thr, act ->
      thr.submit(act, firstValue)
      coroutineScope {
        launch {
          delay(2.seconds)
          thr.submit(act, secondValue)
        }
      }
    }
    var hit = 0
    val verify: (Int, Instant, Mutex) -> Unit = { diff, instant, lock ->
      when (++hit) {
        1 -> {
          Assert.assertEquals(firstValue, diff)
        }
        2 -> {
          Assert.assertEquals(secondValue, diff)
          lock.unlock()
        }
        else -> {
          fail("Got hit $hit, expected >=2")
        }
      }
    }
    runCounterUserActivityTest(true, submit, verify)
  }

  fun testShowdown() {
    val myActivity1 = object : DatabaseBackedCounterUserActivity() {
      override val id: String get() = "testActivity1"
    }
    val myActivity2 = object : DatabaseBackedCounterUserActivity() {
      override val id: String get() = "testActivity2"
    }

    val onDatabaseDeath = mutableListOf<suspend () -> Unit>()

    val expected = mapOf(
      myActivity1.id to 13,
      myActivity2.id to 13*2
    )

    val endedEvents = mutableMapOf<String, Int>()

    timeoutRunBlocking {
      val fakeDatabase = object : IInternalCounterUserActivityDatabase {
        override suspend fun submitDirect(activity: DatabaseBackedCounterUserActivity, diff: Int, instant: Instant) {
          endedEvents[activity.id] = diff
        }

        override fun executeBeforeConnectionClosed(action: suspend () -> Unit) {
          onDatabaseDeath.add(action)
        }
      }

      val submissionLock = Mutex(true)
      val throttlerCoroutine = launch {
        CounterUserActivityDatabaseThrottler(this, fakeDatabase, 10.minutes).apply {
          submit(myActivity1, expected[myActivity1.id]!!)
          submit(myActivity2, (expected[myActivity2.id]!!)/2)
          submit(myActivity2, (expected[myActivity2.id]!!)/2)
          submissionLock.unlock()
        }
      }

      submissionLock.lock()

      for (task in onDatabaseDeath) {
        task()
      }

      throttlerCoroutine.cancel()
    }

    for (expectedValue in expected) {
      Assert.assertTrue(endedEvents.contains(expectedValue.key))
      Assert.assertEquals(expectedValue.value, endedEvents[expectedValue.key])
    }
  }
}