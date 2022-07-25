// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.command.impl.DummyProject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ProjectIndexingHistoryImplTest {

  @Test
  fun `test observation missed the start of suspension (IDEA-281514)`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val time = Instant.now()
    history.stopSuspendingStages(time)
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(1))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(2))
    history.indexingFinished()

    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val time = Instant.now()
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing, time)
    history.suspendStages(time.plusNanos(1))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(2))
    history.indexingFinished()

    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 2`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val time = Instant.now()
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing, time)
    history.suspendStages(time.plusNanos(1))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(2))
    history.stopSuspendingStages(time.plusNanos(3))
    history.indexingFinished()

    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 3`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val time = Instant.now()
    history.suspendStages(time)
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(1))
    history.stopSuspendingStages(time.plusNanos(2))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(3))
    history.indexingFinished()

    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 4`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val time = Instant.now()
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing, time)
    history.stopSuspendingStages(time.plusNanos(1))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(2))
    history.indexingFinished()

    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 5`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val time = Instant.now()
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(1))
    history.stopSuspendingStages(time.plusNanos(2))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing, time.plusNanos(3))
    history.suspendStages(time.plusNanos(4))
    history.stopSuspendingStages(time.plusNanos(5))
    history.indexingFinished()

    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test basic workflow`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val instant = Instant.now()
    history.startStage(ProjectIndexingHistoryImpl.Stage.PushProperties, instant)
    history.stopStage(ProjectIndexingHistoryImpl.Stage.PushProperties, instant.plusNanos(1))
    history.startStage(ProjectIndexingHistoryImpl.Stage.Scanning, instant.plusNanos(2))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Scanning, instant.plusNanos(5))
    history.indexingFinished()

    assertEquals(history.times.indexingDuration, Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ofNanos(1))
    assertEquals(history.times.scanFilesDuration, Duration.ofNanos(3))
  }

  @Test
  fun `test stage with suspension inside`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val instant = Instant.now()
    history.startStage(ProjectIndexingHistoryImpl.Stage.PushProperties, instant)
    history.suspendStages(instant.plusNanos(1))
    history.stopSuspendingStages(instant.plusNanos(4))
    history.stopStage(ProjectIndexingHistoryImpl.Stage.PushProperties, instant.plusNanos(5))
    history.indexingFinished()

    assertEquals(history.times.indexingDuration, Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ofNanos(3))
    assertEquals(history.times.pushPropertiesDuration, Duration.ofNanos(2))
  }
}