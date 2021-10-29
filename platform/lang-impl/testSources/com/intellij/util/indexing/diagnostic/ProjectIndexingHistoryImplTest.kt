// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic

import com.intellij.openapi.command.impl.DummyProject
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.time.Duration

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
}