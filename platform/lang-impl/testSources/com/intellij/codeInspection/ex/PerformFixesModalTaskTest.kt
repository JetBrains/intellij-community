// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex

import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult
import com.intellij.modcommand.ModCommandExecutor.Result
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class PerformFixesModalTaskTest {
  private val projectFixture = projectFixture()

  @Test
  fun `no successful fixes returns zero`() {
    val task = TestPerformFixesTask(projectFixture.get())

    assertEquals(0, task.numberOfSucceededFixes)
  }

  @Test
  fun `only unsuccessful fixes returns zero`() {
    val task = TestPerformFixesTask(projectFixture.get())
    task.recordResult(Result.INTERACTIVE)

    assertEquals(0, task.numberOfSucceededFixes)
  }

  private class TestPerformFixesTask(project: Project) : PerformFixesModalTask(project, CommonProblemDescriptor.EMPTY_ARRAY) {
    fun recordResult(result: BatchExecutionResult) {
      myResultCount[result] = 1
    }

    override fun applyFix(project: Project, descriptor: CommonProblemDescriptor) {
    }
  }
}
