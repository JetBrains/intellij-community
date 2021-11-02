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
    history.stopSuspendingStages()
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.indexingFinished()
    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.suspendStages()
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.indexingFinished()
    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 2`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.suspendStages()
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.stopSuspendingStages()
    history.indexingFinished()
    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 3`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    history.suspendStages()
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.stopSuspendingStages()
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.indexingFinished()
    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 4`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.stopSuspendingStages()
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.indexingFinished()
    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test there may be actions after suspension 5`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    history.startStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.stopSuspendingStages()
    history.stopStage(ProjectIndexingHistoryImpl.Stage.Indexing)
    history.suspendStages()
    history.stopSuspendingStages()
    history.indexingFinished()
    assertTrue(history.times.indexingDuration > Duration.ZERO)
    assertTrue(history.times.suspendedDuration > Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertEquals(history.times.pushPropertiesDuration, Duration.ZERO)
  }

  @Test
  fun `test basic workflow`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    val pushEstimations = runStageWithEstimations(history, ProjectIndexingHistoryImpl.Stage.PushProperties)
    val scanEstimations = runStageWithEstimations(history, ProjectIndexingHistoryImpl.Stage.Scanning)
    history.indexingFinished()
    assertEquals(history.times.indexingDuration, Duration.ZERO)
    assertEquals(history.times.suspendedDuration, Duration.ZERO)
    assertEstimations(history, ProjectIndexingHistoryImpl.Stage.PushProperties, pushEstimations)
    assertEstimations(history, ProjectIndexingHistoryImpl.Stage.Scanning, scanEstimations)
  }

  @Test
  fun `test stage with suspension inside`() {
    val history = ProjectIndexingHistoryImpl(DummyProject.getInstance(), "test", true)
    lateinit var suspendEstimations: Pair<Duration, Duration>
    val pushEstimations = runStageWithEstimations(history, ProjectIndexingHistoryImpl.Stage.PushProperties) {
      suspendEstimations = suspendWithEstimations(history)
    }
    history.indexingFinished()
    assertEquals(history.times.indexingDuration, Duration.ZERO)
    assertEquals(history.times.scanFilesDuration, Duration.ZERO)
    assertSuspensionEstimations(history, suspendEstimations)
    assertEstimations(history, ProjectIndexingHistoryImpl.Stage.PushProperties,
                      Pair(pushEstimations.first.minus(history.times.suspendedDuration),
                           pushEstimations.second.minus(history.times.suspendedDuration)))
  }

  private fun runStageWithEstimations(history: ProjectIndexingHistoryImpl,
                                      stage: ProjectIndexingHistoryImpl.Stage,
                                      block: () -> Unit = {}): Pair<Duration, Duration> {
    val first = Instant.now()
    history.startStage(stage)
    val second = Instant.now()
    block.invoke()
    val third = Instant.now()
    history.stopStage(stage)
    val fourth = Instant.now()
    return Pair(Duration.between(second, third), Duration.between(first, fourth))
  }

  private fun suspendWithEstimations(history: ProjectIndexingHistoryImpl,
                                     block: () -> Unit = {}): Pair<Duration, Duration> {
    val first = Instant.now()
    history.suspendStages()
    val second = Instant.now()
    block.invoke()
    val third = Instant.now()
    history.stopSuspendingStages()
    val fourth = Instant.now()
    return Pair(Duration.between(second, third), Duration.between(first, fourth))
  }

  private fun assertEstimations(history: ProjectIndexingHistoryImpl,
                                stage: ProjectIndexingHistoryImpl.Stage,
                                estimations: Pair<Duration, Duration>) {
    val duration = stage.getProperty().get(history.times as ProjectIndexingHistoryImpl.IndexingTimesImpl)
    assertTrue("Duration $duration, estimations $estimations", duration >= estimations.first)
    assertTrue("Duration $duration, estimations $estimations", duration <= estimations.second)
  }

  private fun assertSuspensionEstimations(history: ProjectIndexingHistoryImpl,
                                          estimations: Pair<Duration, Duration>) {
    val duration = history.times.suspendedDuration
    assertTrue("Duration $duration, estimations $estimations", duration >= estimations.first)
    assertTrue("Duration $duration, estimations $estimations", duration <= estimations.second)
  }
}