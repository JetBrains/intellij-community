// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.Semaphore
import org.assertj.core.api.Assertions.assertThat

class ExternalSystemEventDispatcherTest : LightPlatformTestCase() {

  fun `test invokeOnCompletion`() {
    val parsers = listOf(
      BuildOutputParser { line, reader, messageConsumer ->
        messageConsumer.accept(
          OutputBuildEvent.builder(line)
            .withParentId(reader.parentEventId)
            .build()
        )
        return@BuildOutputParser true
      }
    )

    doInvokeOnCompletionTest(parsers)
  }

  fun `test invokeOnCompletion with bad output parser`() {
    val parsers = listOf(
      BuildOutputParser { _, _, _ -> throw RuntimeException("Bad parser") },
      BuildOutputParser { line, reader, messageConsumer ->
        messageConsumer.accept(
          OutputBuildEvent.builder(line)
            .withParentId(reader.parentEventId)
            .build()
        )
        return@BuildOutputParser true
      }
    )

    doInvokeOnCompletionTest(parsers)
  }

  private fun doInvokeOnCompletionTest(parsers: List<BuildOutputParser>) {
    val eventMessages = mutableListOf<String>()
    ExtensionTestUtil.maskExtensions(ExternalSystemOutputParserProvider.EP_NAME, listOf(TestParserProvider(parsers)), testRootDisposable)
    val semaphore = Semaphore(1)
    val buildDescriptor = DefaultBuildDescriptor(taskId, "test task", "/path", System.currentTimeMillis())
    val dispatcher = ExternalSystemEventDispatcher(taskId, BuildProgressListener { _, event -> eventMessages += event.message })
    dispatcher.use {
      val buildRootProgress = BuildRootProgressImpl(dispatcher)
      buildRootProgress.start("Build started", BuildProgressDescriptorImpl(buildDescriptor))
      dispatcher.invokeOnCompletion { eventMessages += "completion message 1" }
      val task1Progress = buildRootProgress.startChildProgress("sub task1 started")
      dispatcher.appendLine("Task output line 1")
      val task2Progress = buildRootProgress.startChildProgress("sub task2 started")
      dispatcher.appendLine("Task output line 2")
      task2Progress.finish("sub task2 finished", SuccessResultImpl())
      task1Progress.finish("sub task1 finished", SuccessResultImpl())
      buildRootProgress.finish("Build finished", SuccessResultImpl())
      dispatcher.invokeOnCompletion { throw RuntimeException("Bad happens") }
      dispatcher.invokeOnCompletion { eventMessages += "completion message 2" }
      dispatcher.invokeOnCompletion { semaphore.up() }
      dispatcher.appendLine("Task output line 3")
    }
    dispatcher.invokeOnCompletion { eventMessages += "Late completion message" }

    assertTrue(semaphore.waitFor(500))

    assertThat(eventMessages)
      .doesNotContain("Late completion message")
      .hasSize(11)
    assertSameElements(eventMessages.take(8),
                       "Build started",
                       "sub task1 started",
                       "sub task2 started",
                       "sub task2 finished",
                       "sub task1 finished",
                       "Task output line 1",
                       "Task output line 2",
                       "Task output line 3")

    assertOrderedEquals(eventMessages.takeLast(3),
                        "completion message 1",
                        "Build finished",
                        "completion message 2")
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
