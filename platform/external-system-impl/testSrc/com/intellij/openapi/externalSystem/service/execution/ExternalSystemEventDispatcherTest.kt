// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.execution

import com.intellij.build.BuildProgressListener
import com.intellij.build.BuildViewSettingsProvider
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.platform.externalSystem.testFramework.ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.concurrency.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CopyOnWriteArrayList
import java.util.function.Consumer

@TestApplication
@ParameterizedClass
@ValueSource(booleans = [false, true])
class ExternalSystemEventDispatcherTest(useSingleConsole: Boolean) {

  private val disposable by disposableFixture()

  private val settings: BuildViewSettingsProvider by testFixture {
    initialized(object : BuildViewSettingsProvider {
      override fun isSingleBuildConsoleView(): Boolean = useSingleConsole
    }) {}
  }

  private val project by projectFixture()

  @Test
  fun `test parser execution flow`() {
    val badParser = RecordingBuildOutputParser { throw RuntimeException("Bad parser") }
    val goodParser = RecordingBuildOutputParser { true }
    val skippedParser = RecordingBuildOutputParser { true }
    val parsers = TestOutputParserProvider(listOf(badParser, goodParser, skippedParser))
    ExtensionTestUtil.maskExtensions(ExternalSystemOutputParserProvider.EP_NAME, listOf(parsers), disposable)

    val listener = RecordingBuildProgressListener()
    val semaphore = Semaphore(1)
    val taskId = ExternalSystemTaskId.create(TEST_EXTERNAL_SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, project)
    val buildDescriptor = DefaultBuildDescriptor(taskId, "test task", "/path", System.currentTimeMillis())
    val dispatcher = ExternalSystemEventDispatcher(taskId, listener, settings)
    dispatcher.use {
      val buildRootProgress = BuildRootProgressImpl { buildId, event -> dispatcher.notifyBuildEvent(buildId, event) }
      buildRootProgress.start("Build started", BuildProgressDescriptorImpl(buildDescriptor))
      dispatcher.invokeOnCompletion { listener.messages += "completion message 1" }
      val task1Progress = buildRootProgress.startChildProgress("sub task1 started")
      dispatcher.notifyTextAvailable("Task output line 1\n", ProcessOutputType.STDOUT)
      val task2Progress = buildRootProgress.startChildProgress("sub task2 started")
      dispatcher.notifyTextAvailable("Task output line 2\n", ProcessOutputType.STDOUT)
      task2Progress.finish("sub task2 finished", SuccessResultImpl())
      task1Progress.finish("sub task1 finished", SuccessResultImpl())
      buildRootProgress.finish("Build finished", SuccessResultImpl())
      dispatcher.invokeOnCompletion { throw RuntimeException("Bad happens") }
      dispatcher.invokeOnCompletion { listener.messages += "completion message 2" }
      dispatcher.invokeOnCompletion { semaphore.up() }
      dispatcher.notifyTextAvailable("Task output line 3\n", ProcessOutputType.STDOUT)
    }
    dispatcher.invokeOnCompletion { listener.messages += "Late completion message" }

    assertTrue(semaphore.waitFor(500))

    val progressMessages = listOf(
      "Build started",
      "sub task1 started",
      "sub task2 started",
      "sub task2 finished",
      "sub task1 finished",
    )
    val outputLines = listOf(
      "Task output line 1",
      "Task output line 2",
      "Task output line 3"
    )
    val completionMessages = listOf(
      "completion message 1",
      "Build finished",
      "completion message 2",
    )
    val progressAndOutputMessages = when (settings.isSingleBuildConsoleView) {
      true -> progressMessages + outputLines.map { it + "\n" }
      else -> progressMessages
    }

    assertThat(listener.messages)
      .doesNotContain("Late completion message")
      .hasSize(progressAndOutputMessages.size + completionMessages.size)
    assertThat(listener.messages.take(progressAndOutputMessages.size))
      .containsExactlyInAnyOrderElementsOf(progressAndOutputMessages)
    assertThat(listener.messages.takeLast(completionMessages.size))
      .containsExactlyElementsOf(completionMessages)

    assertThat(badParser.lines)
      .describedAs("The failing parser should be invoked for each line")
      .containsExactlyElementsOf(outputLines)
    assertThat(goodParser.lines)
      .describedAs("The parser after the failing one should be invoked for each line")
      .containsExactlyElementsOf(outputLines)
    assertThat(skippedParser.lines)
      .describedAs("The parser after the consuming one should not be invoked")
      .isEmpty()
  }

  private class RecordingBuildProgressListener : BuildProgressListener {

    val messages = CopyOnWriteArrayList<String>()

    override fun onEvent(buildId: Any, event: BuildEvent) {
      messages.add(event.message)
    }
  }

  private class RecordingBuildOutputParser(private val result: () -> Boolean) : BuildOutputParser {

    val lines = CopyOnWriteArrayList<String>()

    override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in BuildEvent>): Boolean {
      lines.add(line)
      return result()
    }
  }

  private class TestOutputParserProvider(val parsers: List<BuildOutputParser>) : ExternalSystemOutputParserProvider {
    override fun getExternalSystemId(): ProjectSystemId = TEST_EXTERNAL_SYSTEM_ID
    override fun getBuildOutputParsers(taskId: ExternalSystemTaskId): List<BuildOutputParser> = parsers
  }
}
