// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.process.*
import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.testApp.SimpleCliApp
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import kotlinx.coroutines.*
import org.assertj.core.api.Assertions
import java.lang.management.ThreadInfo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TerminalExecutionConsoleTest : BasePlatformTestCase() {

  override fun runInDispatchThread(): Boolean = false

  fun `test disposing console should stop emulator thread`(): Unit = timeoutRunBlocking(DEFAULT_TEST_TIMEOUT) {
    val processHandler = NopProcessHandler()
    val console = withContext(Dispatchers.UI) {
      TerminalExecutionConsoleBuilder(project).build()
    }
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    awaitCondition { findEmulatorThreadInfo() != null }
    assertNotNull(findEmulatorThreadInfo())
    withContext(Dispatchers.UI) {
      Disposer.dispose(console)
    }
    awaitCondition { findEmulatorThreadInfo() == null }
    assertNull(findEmulatorThreadInfo())
    processHandler.destroyProcess()
    processHandler.awaitTerminated()
  }

  private suspend fun awaitCondition(condition: () -> Boolean) {
    while (!condition()) {
      delay(100.milliseconds)
    }
  }

  private fun findEmulatorThreadInfo(): ThreadInfo? {
    val threadInfos = ThreadDumper.getThreadInfos()
    return threadInfos.find { it.threadName.startsWith("TerminalEmulator-") }
  }

  fun `test convert LF to CRLF for processes without PTY`(): Unit = timeoutRunBlockingWithConsole { console ->
    val processHandler = OSProcessHandler(MockPtyBasedProcess(false), "my command", Charsets.UTF_8)
    console.attachToProcess(processHandler)
    @Suppress("DEPRECATION")
    console.withConvertLfToCrlfForNonPtyProcess(true)
    TestProcessTerminationMessage.attach(processHandler)
    processHandler.startNotify()
    processHandler.notifyTextAvailable("Foo\nBar\nBaz", ProcessOutputTypes.STDOUT)
    processHandler.destroyProcess()
    console.awaitOutputContainsSubstring(substringToFind = TestProcessTerminationMessage.getMessage(MockPtyBasedProcess.EXIT_CODE))
    val output = TerminalOutput.collect(console.terminalWidget)
    output.assertLinesAre(listOf(
      "my command",
      "Foo",
      "Bar",
      "Baz",
      TestProcessTerminationMessage.getMessage(MockPtyBasedProcess.EXIT_CODE)
    ))
    processHandler.assertTerminated()
  }

  fun `test support ColoredProcessHandler`(): Unit = timeoutRunBlockingWithConsole { console ->
    val processHandler = ColoredProcessHandler(MockPtyBasedProcess(true), "my command line", Charsets.UTF_8)
    assertTrue(TerminalExecutionConsole.isAcceptable(processHandler))
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    processHandler.notifyTextAvailable("\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[32mFoo\u001b[0m", ProcessOutputTypes.STDOUT)
    TestProcessTerminationMessage.attach(processHandler)
    processHandler.destroyProcess()
    console.awaitOutputContainsSubstring(substringToFind = TestProcessTerminationMessage.getMessage(MockPtyBasedProcess.EXIT_CODE))
    val output = TerminalOutput.collect(console.terminalWidget)
    output.assertLinesAre(listOf(
      "my command line",
      "Foo",
      TestProcessTerminationMessage.getMessage(MockPtyBasedProcess.EXIT_CODE)
    ))
    output.assertContainsChunk(TerminalOutputChunk("Foo", TextStyle(TerminalColor(2), null)))
    processHandler.assertTerminated()
  }

  fun `test support OSProcessHandler`(): Unit = timeoutRunBlockingWithConsole { console ->
    val processHandler = OSProcessHandler(MockPtyBasedProcess(true), "command line", Charsets.UTF_8)
    assertTrue(TerminalExecutionConsole.isAcceptable(processHandler))
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    processHandler.notifyTextAvailable("\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[32mFoo\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[43mBar\u001b[0m", ProcessOutputTypes.STDOUT)
    console.awaitOutputContainsSubstring(substringToFind = "Bar")
    console.assertOutputStartsWithLines(expectedStartLines = listOf("command line", "FooBar"))
    val output = TerminalOutput.collect(console.terminalWidget)
    output.assertContainsChunk(TerminalOutputChunk("Foo", TextStyle(TerminalColor(2), null)))
    output.assertContainsChunk(TerminalOutputChunk("Bar", TextStyle(null, TerminalColor(3))))
    processHandler.destroyProcess()
    processHandler.awaitTerminated()
  }

  fun `test same styled consecutive texts are merged`(): Unit = timeoutRunBlockingWithConsole { console ->
    val processHandler = NopProcessHandler()
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    processHandler.notifyTextAvailable("\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[32mThis", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable(" is", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable(" the first\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[32m chu", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("nk.\u001b[0m", ProcessOutputTypes.STDOUT)
    console.awaitOutputContainsSubstring(substringToFind = "This is the first chunk.")
    processHandler.notifyTextAvailable("\u001b[33m This is a different chunk.", ProcessOutputTypes.STDOUT)
    console.awaitOutputContainsSubstring(substringToFind = " This is a different chunk.")

    val output = TerminalOutput.collect(console.terminalWidget)
    Assertions.assertThat(output.contains("This is the first chunk. This is a different chunk."))
      .describedAs(output.lines.flatMap { it.outputChunks }.toString())
      .isFalse
    processHandler.destroyProcess()
    processHandler.awaitTerminated()
  }

  fun `test basic SimpleCliApp java process`(): Unit = timeoutRunBlockingWithConsole { console ->
    val textToPrint = "Hello, World"
    val javaCommand = SimpleCliApp.NonRuntime.createCommand(SimpleCliApp.Options(
      textToPrint, 0, null
    ))
    val processHandler = createTerminalProcessHandler(this, javaCommand)
    console.attachToProcess(processHandler)
    TestProcessTerminationMessage.attach(processHandler)
    processHandler.startNotify()
    console.awaitOutputEndsWithLines(expectedEndLines = listOf(
      textToPrint,
      TestProcessTerminationMessage.getMessage(0)
    ))
    console.assertOutputStartsWithLines(expectedStartLines = listOf(javaCommand.commandLine))
    processHandler.assertTerminated()
  }

  fun `test basic SimpleCliApp java process with non-zero exit code`(): Unit = timeoutRunBlockingWithConsole { console ->
    val textToPrint = "Something went wrong"
    val javaCommand = SimpleCliApp.NonRuntime.createCommand(SimpleCliApp.Options(
      textToPrint, 42, null
    ))
    val processHandler = createTerminalProcessHandler(this, javaCommand)
    console.attachToProcess(processHandler)
    TestProcessTerminationMessage.attach(processHandler)
    processHandler.startNotify()
    console.awaitOutputEndsWithLines(expectedEndLines = listOf(
      textToPrint,
      TestProcessTerminationMessage.getMessage(42)
    ))
    console.assertOutputStartsWithLines(expectedStartLines = listOf(javaCommand.commandLine))
    processHandler.assertTerminated()
  }

  fun `test read input in SimpleCliApp java process`(): Unit = timeoutRunBlockingWithConsole { console ->
    val textToPrint = "Enter your name:"
    val javaCommand = SimpleCliApp.NonRuntime.createCommand(SimpleCliApp.Options(
      textToPrint, 0, "exit"
    ))
    val processHandler = createTerminalProcessHandler(this, javaCommand)
    console.attachToProcess(processHandler)
    TestProcessTerminationMessage.attach(processHandler)
    processHandler.startNotify()
    console.awaitOutputEndsWithLines(expectedEndLines = listOf(textToPrint))
    processHandler.writeToStdinAndHitEnter("exit")
    console.awaitOutputEndsWithLines(expectedEndLines = listOf(
      textToPrint + "exit",
      "Read line: exit",
      "",
      TestProcessTerminationMessage.getMessage(0)
    ))
    console.assertOutputStartsWithLines(expectedStartLines = listOf(javaCommand.commandLine))
    processHandler.assertTerminated()
  }

  private fun <T> timeoutRunBlockingWithConsole(
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    action: suspend CoroutineScope.(TerminalExecutionConsole) -> T,
  ): T = timeoutRunBlocking(timeout) {
    val console = withContext(Dispatchers.UI) {
      TerminalExecutionConsoleBuilder(project).build()
    }
    try {
      action(console)
    }
    finally {
      withContext(Dispatchers.UI) {
        Disposer.dispose(console)
      }
    }
  }

}

private val DEFAULT_TEST_TIMEOUT: Duration = 60.seconds
