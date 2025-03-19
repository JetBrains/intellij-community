// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.command.impl.DummyProject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ProjectScanningHistoryImplTest {

  @Test
  fun `test observation missed the start of suspension (IDEA-281514)`() {
    val history = withHistory { history, time ->
      history.stopSuspendingStages(time)
      history.startStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, time.plusNanos(1))
      history.stopStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, time.plusNanos(2))
    }

    assertTrue(history.times.delayedPushPropertiesStageDuration > Duration.ZERO)
    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
    assertEquals(history.times.pausedDuration, Duration.ZERO)
    assertEquals(history.times.concurrentHandlingWallTimeWithoutPauses, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension`() {
    val history = withHistory { history, time ->
      history.startStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, time)
      history.suspendStages(time.plusNanos(1))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, time.plusNanos(3))
    }

    assertTrue(history.times.concurrentHandlingWallTimeWithoutPauses > Duration.ZERO)
    assertEquals(history.times.pausedDuration, Duration.ofNanos(2))
    assertEquals(history.times.delayedPushPropertiesStageDuration, Duration.ZERO)
    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
  }

  @Test
  fun `test usual start suspended picture`() {
    val history = withHistory { history, time ->
      history.suspendStages(time)
      history.startStage(ProjectScanningHistoryImpl.Stage.CreatingIterators, time.plusNanos(10))
      history.stopSuspendingStages(time.plusNanos(11))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CreatingIterators, time.plusNanos(15))
    }

    assertEquals(history.times.creatingIteratorsDuration, Duration.ofNanos(4))
    assertEquals(history.times.pausedDuration, Duration.ofNanos(11))
    assertEquals(history.times.concurrentHandlingWallTimeWithoutPauses, Duration.ZERO)
    assertEquals(history.times.delayedPushPropertiesStageDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 2`() {
    val history = withHistory { history, time ->
      history.startStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, time)
      history.suspendStages(time.plusNanos(1))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, time.plusNanos(2))
      history.stopSuspendingStages(time.plusNanos(3))
    }

    assertTrue(history.times.concurrentHandlingWallTimeWithoutPauses > Duration.ZERO)
    assertTrue(history.times.pausedDuration > Duration.ZERO)
    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
    assertEquals(history.times.delayedPushPropertiesStageDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 3`() {
    val history = withHistory { history, time ->
      history.suspendStages(time)
      history.startStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, time.plusNanos(1))
      history.stopSuspendingStages(time.plusNanos(2))
      history.stopStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, time.plusNanos(3))
    }

    assertTrue(history.times.delayedPushPropertiesStageDuration > Duration.ZERO)
    assertTrue(history.times.pausedDuration > Duration.ZERO)
    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
    assertEquals(history.times.concurrentHandlingWallTimeWithoutPauses, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 4`() {
    val history = withHistory { history, time ->
      history.startStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, time)
      history.stopSuspendingStages(time.plusNanos(1))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, time.plusNanos(2))
    }

    assertTrue(history.times.concurrentHandlingWallTimeWithoutPauses > Duration.ZERO)
    assertTrue(history.times.pausedDuration > Duration.ZERO)
    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
    assertEquals(history.times.delayedPushPropertiesStageDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 5`() {
    val history = withHistory { history, time ->
      history.startStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, time.plusNanos(1))
      history.stopSuspendingStages(time.plusNanos(2))
      history.stopStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, time.plusNanos(3))
      history.suspendStages(time.plusNanos(4))
      history.stopSuspendingStages(time.plusNanos(5))
    }

    assertTrue(history.times.delayedPushPropertiesStageDuration > Duration.ZERO)
    assertTrue(history.times.pausedDuration > Duration.ZERO)
    assertEquals(history.times.concurrentHandlingWallTimeWithoutPauses, Duration.ZERO)
    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
  }

  @Test
  fun `test basic workflow`() {
    val history = withHistory { history, instant ->
      history.startStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, instant)
      history.stopStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, instant.plusNanos(1))
      history.startStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, instant.plusNanos(2))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, instant.plusNanos(5))
    }

    assertEquals(history.times.creatingIteratorsDuration, Duration.ZERO)
    assertEquals(history.times.pausedDuration, Duration.ZERO)
    assertEquals(history.times.delayedPushPropertiesStageDuration, Duration.ofNanos(1))
    assertEquals(history.times.concurrentHandlingWallTimeWithoutPauses, Duration.ofNanos(3))
  }

  @Test
  fun `test stage with suspension inside`() {
    val history = withHistory { history, instant ->
      history.startStage(ProjectScanningHistoryImpl.Stage.CreatingIterators, instant)
      history.suspendStages(instant.plusNanos(1))
      history.stopSuspendingStages(instant.plusNanos(4))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CreatingIterators, instant.plusNanos(5))
    }

    assertEquals(history.times.delayedPushPropertiesStageDuration, Duration.ZERO)
    assertEquals(history.times.concurrentHandlingWallTimeWithoutPauses, Duration.ZERO)
    assertEquals(history.times.pausedDuration, Duration.ofNanos(3))
    assertEquals(history.times.creatingIteratorsDuration, Duration.ofNanos(2))
  }

  @Test
  fun `test many starts of suspension`() {
    /*
    Assertion failed: Two suspension starts, no stops. Events [
    StageEvent(stage=Scanning, started=true, instant=2022-05-27T10:24:51.385020Z),
    SuspensionEvent(started=true, instant=2022-05-27T10:24:51.442590Z),
    SuspensionEvent(started=true, instant=2022-05-27T10:24:51.442949Z),
    SuspensionEvent(started=true, instant=2022-05-27T10:24:51.443051Z),
    SuspensionEvent(started=false, instant=2022-05-27T10:24:51.443158Z),
    StageEvent(stage=Scanning, started=false, instant=2022-05-27T10:24:51.471022Z)]
     */
    val history = withHistory { history, instant ->
      history.startStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, instant)
      history.suspendStages(instant.plusNanos(1))
      history.suspendStages(instant.plusNanos(2))
      history.suspendStages(instant.plusNanos(3))
      history.stopSuspendingStages(instant.plusNanos(4))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, instant.plusNanos(5))
    }

    assertEquals(Duration.ZERO, history.times.delayedPushPropertiesStageDuration)
    assertEquals(Duration.ZERO, history.times.creatingIteratorsDuration)
    assertEquals(Duration.ofNanos(3), history.times.pausedDuration)
    assertEquals(Duration.ofNanos(2), history.times.concurrentHandlingWallTimeWithoutPauses)
  }

  private fun withHistory(changer: (history: ProjectScanningHistoryImpl, instant: Instant) -> Unit): ProjectScanningHistoryImpl {
    val history = ProjectScanningHistoryImpl(DummyProject.getInstance(), "test", ScanningType.FULL)
    changer.invoke(history, Instant.now())
    history.scanningFinished()
    return history
  }

  @Test
  fun `test failure in TC`() { //IJPL-2957
    /*
    java.lang.Throwable: Assertion failed: DumbMode is not started, tries to stop.
    Events
    [StageEvent(stage=DelayedPushProperties, started=true, instant=2024-02-03T06:07:00.949673Z),
     StageEvent(stage=DelayedPushProperties, started=false, instant=2024-02-03T06:07:00.949679Z),
     StageEvent(stage=CreatingIterators, started=true, instant=2024-02-03T06:07:00.950195Z),
     StageEvent(stage=CreatingIterators, started=false, instant=2024-02-03T06:07:00.950198Z),
     StageEvent(stage=CollectingIndexableFiles, started=true, instant=2024-02-03T06:07:00.950199Z),
     StageEvent(stage=CollectingIndexableFiles, started=false, instant=2024-02-03T06:07:00.955398Z),
     StageEvent(stage=DumbMode, started=false, instant=2024-02-03T06:07:00.955651Z)]
   */
    val history = withHistory { history, instant ->
      history.startStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, instant)
      history.stopStage(ProjectScanningHistoryImpl.Stage.DelayedPushProperties, instant.plusNanos(1))
      history.startStage(ProjectScanningHistoryImpl.Stage.CreatingIterators, instant.plusNanos(2))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CreatingIterators, instant.plusNanos(4))
      history.startStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, instant.plusNanos(6))
      history.stopStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, instant.plusNanos(10))
      history.stopStage(ProjectScanningHistoryImpl.Stage.DumbMode, instant.plusSeconds(12))
    }

    assertEquals(Duration.ofNanos(1), history.times.delayedPushPropertiesStageDuration)
    assertEquals(Duration.ofNanos(2), history.times.creatingIteratorsDuration)
    assertEquals(Duration.ZERO, history.times.pausedDuration)
    assertEquals(Duration.ofNanos(4), history.times.concurrentHandlingWallTimeWithoutPauses)
    assertEquals(12, history.times.dumbModeWithoutPausesDuration.toSeconds())
    //these values are controlled by other means
    assertEquals(Duration.ZERO, history.times.dumbModeWithPausesDuration)
    assertEquals(null, history.times.dumbModeStart)
  }
}