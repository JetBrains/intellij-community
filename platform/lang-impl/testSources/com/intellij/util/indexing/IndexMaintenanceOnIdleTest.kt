// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class IndexMaintenanceOnIdleTest {
  @Test
  fun `maintenance intervals grow by the default multiplier after each completed cycle`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(events, 20.minutes, timeSource = testScheduler.timeSource) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(20.minutes)
    assertEquals(0, maintenanceCount, "Maintenance must wait for the full idle delay")
    runCurrent()
    assertEquals(1, maintenanceCount)

    advanceTimeBy(29.minutes + 59.seconds)
    runCurrent()
    assertEquals(1, maintenanceCount, "The first repeat must wait for 20 minutes multiplied by 1.5")
    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(2, maintenanceCount, "The timer must repeat maintenance without another idle event")

    advanceTimeBy(44.minutes + 59.seconds)
    runCurrent()
    assertEquals(2, maintenanceCount, "The next repeat must wait for 30 minutes multiplied by 1.5")
    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(3, maintenanceCount)
  }

  @Test
  fun `user input resets the initial idle delay`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(events, 20.minutes, timeSource = testScheduler.timeSource) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(10.minutes)
    events.emit(Unit)
    runCurrent()
    advanceTimeBy(10.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "Input must reset the idle delay")

    advanceTimeBy(10.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)
  }

  @Test
  fun `user input resets idleness and the maintenance interval`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(events, 20.minutes, timeSource = testScheduler.timeSource) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)

    advanceTimeBy(5.minutes)
    events.emit(Unit)
    runCurrent()
    advanceTimeBy(10.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount, "Input must cancel the pending repeat")

    advanceTimeBy(10.minutes)
    runCurrent()
    assertEquals(2, maintenanceCount, "User input must restore the initial twenty-minute interval")

    advanceTimeBy(29.minutes + 59.seconds)
    runCurrent()
    assertEquals(2, maintenanceCount, "After the reset, the next interval must grow from twenty to thirty minutes")
    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(3, maintenanceCount)
  }

  @Test
  fun `user input during maintenance preserves the initial interval`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(events, 20.minutes, timeSource = testScheduler.timeSource) {
        maintenanceCount++
        if (maintenanceCount == 1) {
          // Simulate a user event processed by the modal progress
          events.tryEmit(Unit)
          testScheduler.runCurrent()
        }
        true
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)

    advanceTimeBy(19.minutes + 59.seconds)
    runCurrent()
    assertEquals(1, maintenanceCount, "User input during maintenance must start a new idle period")
    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(2, maintenanceCount, "Completing maintenance must preserve the reset from a user event during the cycle")
  }

  @Test
  fun `background work defers the first maintenance until a later attempt`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var activity: String? = "VFS refresh"
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 20.minutes,
        backgroundActivities = { BackgroundActivities(activity) },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "Background work must prevent maintenance after the user idle delay")

    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "Every attempt must check whether background work is still active")

    activity = null
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "The previous poll was busy, so this interval cannot count as quiet")
    advanceTimeBy(1.minutes)
    assertEquals(0, maintenanceCount, "Maintenance must wait until the next poll")
    runCurrent()
    assertEquals(1, maintenanceCount, "Maintenance must resume without another user event when background work finishes")

    advanceTimeBy(30.minutes)
    runCurrent()
    assertEquals(2, maintenanceCount, "Only a completed cycle must increase the maintenance interval")
  }

  @Test
  fun `background work defers repeated maintenance`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var activity: String? = null
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 20.minutes,
        backgroundActivities = { BackgroundActivities(activity) },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)

    activity = "indexing"
    advanceTimeBy(30.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount, "Background work must also prevent a repeated maintenance cycle")

    activity = null
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount, "Both ends of the interval must be free of background work")
    advanceTimeBy(1.minutes)
    assertEquals(1, maintenanceCount, "The deferred repeat must wait for the next poll")
    runCurrent()
    assertEquals(2, maintenanceCount)
  }

  @Test
  fun `user input resets the idle delay while maintenance is deferred`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var activity: String? = "scanning"
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 20.minutes,
        backgroundActivities = { BackgroundActivities(activity) },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount)

    events.emit(Unit)
    runCurrent()
    activity = null
    advanceTimeBy(15.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "Input must cancel the deferred attempt even after background work finishes")

    advanceTimeBy(5.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount, "Maintenance must wait for a new user idle delay")
  }

  @Test
  fun `counter changes between polls reset the quiet period`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    val scanning = SimpleModificationTracker()
    val indexing = SimpleModificationTracker()
    val vfs = SimpleModificationTracker()
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 5.minutes,
        backgroundActivities = {
          BackgroundActivities(counters = mapOf(
            scanning to scanning.modificationCount,
            indexing to indexing.modificationCount,
            vfs to vfs.modificationCount,
          ))
        },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(4.minutes + 30.seconds)
    runCurrent()
    // Each task finishes between polls, so only its counter can reveal the activity
    scanning.incModificationCount()
    advanceTimeBy(30.seconds)
    runCurrent()
    assertEquals(0, maintenanceCount, "A completed scan must reset the quiet period before the user idle delay expires")

    indexing.incModificationCount()
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "A later indexing task must reset the same quiet period")

    vfs.incModificationCount()
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "A VFS change between polls must reset the quiet period even without an observed refresh")

    advanceTimeBy(1.minutes)
    assertEquals(0, maintenanceCount, "Maintenance must wait until the next poll")
    runCurrent()
    assertEquals(1, maintenanceCount, "All counters must stay unchanged for a full quiet minute")
  }

  @Test
  fun `observed VFS refresh resets the quiet period`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var activity: String? = null
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 5.minutes,
        backgroundActivities = { BackgroundActivities(activity) },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(4.minutes + 30.seconds)
    runCurrent()
    activity = "VFS refresh"
    advanceTimeBy(30.seconds)
    runCurrent()
    assertEquals(0, maintenanceCount, "A refresh at the current poll must prevent maintenance")
    activity = null

    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(0, maintenanceCount, "A refresh at the previous poll must also prevent maintenance")

    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)
  }

  @Test
  fun `power save mode checks every five minutes`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var maintenanceCount = 0
    var pollCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 5.minutes, delayMultiplier = 3.0,
        backgroundActivities = {
          pollCount++
          BackgroundActivities()
        },
        timeSource = testScheduler.timeSource,
        powerSaveMode = { true },
      ) {
        maintenanceCount++
        true
      }
    }

    runCurrent()
    advanceTimeBy(4.minutes + 59.seconds)
    runCurrent()
    assertEquals(1, pollCount, "Power save mode must keep only the initial snapshot until five minutes pass")
    assertEquals(0, maintenanceCount)

    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(2, pollCount)
    assertEquals(1, maintenanceCount)

    advanceTimeBy(15.minutes)
    runCurrent()
    assertEquals(5, pollCount, "The loop must keep checking every five minutes during the repeat delay")
    assertEquals(2, maintenanceCount, "Power save mode must preserve the maintenance repeat delay")
  }

  @Test
  fun `each poll uses the current power save mode for the next delay`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var powerSaveMode = false
    var pollCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 20.minutes,
        backgroundActivities = {
          pollCount++
          BackgroundActivities()
        },
        timeSource = testScheduler.timeSource,
        powerSaveMode = { powerSaveMode },
      ) { error("The user idle delay has not elapsed") }
    }

    runCurrent()
    powerSaveMode = true
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(2, pollCount)

    advanceTimeBy(4.minutes + 59.seconds)
    runCurrent()
    assertEquals(2, pollCount, "Enabling power save mode must extend the next delay")

    powerSaveMode = false
    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(3, pollCount)
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(4, pollCount, "Disabling power save mode must restore the one-minute delay")
  }

  @Test
  fun `repeat delay starts at completion and counter snapshots stay current during it`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    val vfs = SimpleModificationTracker()
    var maintenanceCount = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 5.minutes, delayMultiplier = 3.0,
        backgroundActivities = { BackgroundActivities(counters = mapOf(vfs to vfs.modificationCount)) },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        if (maintenanceCount == 1) {
          // Simulate time spent in the synchronous maintenance callback
          testScheduler.advanceTimeBy(2.minutes)
        }
        true
      }
    }

    advanceTimeBy(5.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)
    vfs.incModificationCount()

    advanceTimeBy(14.minutes + 59.seconds)
    runCurrent()
    assertEquals(1, maintenanceCount, "The repeat delay must start after the maintenance callback returns")

    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(2, maintenanceCount, "An old VFS change during the repeat delay must not defer the next cycle")
  }

  @Test
  fun `a skipped attempt preserves the interval after the last completed cycle`() = runTest {
    var attempts = 0
    var completed = 0
    backgroundScope.launch {
      runMaintenanceOnIdle(MutableSharedFlow(), 20.minutes, timeSource = testScheduler.timeSource) {
        attempts++
        if (attempts == 2) {
          false
        }
        else {
          completed++
          true
        }
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(1, completed)
    advanceTimeBy(30.minutes)
    runCurrent()
    assertEquals(2, attempts)
    assertEquals(1, completed, "A skipped attempt must not count as completed maintenance")
    advanceTimeBy(1.minutes)
    runCurrent()
    assertEquals(2, completed, "A skipped attempt must allow another try at the next poll")

    advanceTimeBy(44.minutes + 59.seconds)
    runCurrent()
    assertEquals(2, completed, "The skipped attempt must not increase the forty-five-minute repeat delay")
    advanceTimeBy(1.seconds)
    runCurrent()
    assertEquals(3, completed)
  }

  @Test
  fun `scope cancellation stops the repeat timer`() = runTest {
    val events = MutableSharedFlow<Unit>(replay = 1)
    events.emit(Unit)
    var maintenanceCount = 0
    var pollCount = 0
    val job = backgroundScope.launch {
      runMaintenanceOnIdle(
        events, 20.minutes,
        backgroundActivities = {
          pollCount++
          BackgroundActivities()
        },
        timeSource = testScheduler.timeSource,
      ) {
        maintenanceCount++
        true
      }
    }

    advanceTimeBy(20.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount)
    job.cancel()
    runCurrent()
    val pollsBeforeCancellation = pollCount

    advanceTimeBy(60.minutes)
    runCurrent()
    assertEquals(1, maintenanceCount, "The timer must stop with its owning scope")
    assertEquals(pollsBeforeCancellation, pollCount, "The activity polling must stop with its owning scope")
  }
}
