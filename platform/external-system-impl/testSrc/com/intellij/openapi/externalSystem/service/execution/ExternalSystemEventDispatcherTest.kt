// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.Semaphore
import org.assertj.core.api.Assertions.assertThat
import java.util.concurrent.atomic.LongAdder

class ExternalSystemEventDispatcherTest : LightPlatformTestCase() {

  fun `test parser execution flow`() {
    val parserInvocations = LongAdder()
    val parsers = listOf(
      BuildOutputParser { _, _, _ -> throw RuntimeException("Bad parser") },
      BuildOutputParser { _, _, _ ->
        parserInvocations.increment()
        return@BuildOutputParser true
      },
      BuildOutputParser { _, _, _ ->
        fail("This parser should not be invoked")
        throw IllegalStateException()
      }
    )

    val eventMessages = mutableListOf<String>()
    ExtensionTestUtil.maskExtensions(ExternalSystemOutputParserProvider.EP_NAME, listOf(TestParserProvider(parsers)), testRootDisposable)
    val semaphore = Semaphore(1)
    val buildDescriptor = DefaultBuildDescriptor(taskId, "test task", "/path", System.currentTimeMillis())
    val dispatcher = ExternalSystemEventDispatcher(
      taskId,
      BuildProgressListener { _, event -> eventMessages += event.message },
      BuildViewSettingsProvider.EMPTY
    )
    dispatcher.use {
      val buildRootProgress = BuildRootProgressImpl { buildId, event -> dispatcher.notifyBuildEvent(buildId, event) }
      buildRootProgress.start("Build started", BuildProgressDescriptorImpl(buildDescriptor))
      dispatcher.invokeOnCompletion { eventMessages += "completion message 1" }
      val task1Progress = buildRootProgress.startChildProgress("sub task1 started")
      dispatcher.notifyTextAvailable("Task output line 1\n", ProcessOutputType.STDOUT)
      val task2Progress = buildRootProgress.startChildProgress("sub task2 started")
      dispatcher.notifyTextAvailable("Task output line 2\n", ProcessOutputType.STDOUT)
      task2Progress.finish("sub task2 finished", SuccessResultImpl())
      task1Progress.finish("sub task1 finished", SuccessResultImpl())
      buildRootProgress.finish("Build finished", SuccessResultImpl())
      dispatcher.invokeOnCompletion { throw RuntimeException("Bad happens") }
      dispatcher.invokeOnCompletion { eventMessages += "completion message 2" }
      dispatcher.invokeOnCompletion { semaphore.up() }
      dispatcher.notifyTextAvailable("Task output line 3\n", ProcessOutputType.STDOUT)
    }
    dispatcher.invokeOnCompletion { eventMessages += "Late completion message" }

    assertTrue(semaphore.waitFor(500))

    assertThat(eventMessages)
      .doesNotContain("Late completion message")
      .hasSize(11)
    assertSameElements(eventMessages.take(8),
                       "Build started",
                       "sub task1 started",
                       "Task output line 1\n",
                       "sub task2 started",
                       "Task output line 2\n",
                       "sub task2 finished",
                       "sub task1 finished",
                       "Task output line 3\n")

    assertOrderedEquals(eventMessages.takeLast(3),
                        "completion message 1",
                        "Build finished",
                        "completion message 2")

    assertEquals(3, parserInvocations.toInt())
  }

  private class TestParserProvider(val parsers: List<BuildOutputParser>) : ExternalSystemOutputParserProvider {
    override fun getExternalSystemId(): ProjectSystemId = testSystemId

    override fun getBuildOutputParsers(taskId: ExternalSystemTaskId): List<BuildOutputParser> {
      return parsers
    }
  }

  companion object {
    private val testSystemId = ProjectSystemId("ExternalSystemEventDispatcherTest")
    private val taskId = ExternalSystemTaskId.create(testSystemId, ExternalSystemTaskType.EXECUTE_TASK, "ExternalSystemEventDispatcherTest")
  }
}
