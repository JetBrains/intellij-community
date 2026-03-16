// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.process.ColoredProcessHandler
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.terminal.testApp.SimpleCliApp
import com.intellij.terminal.testApp.SimplePrinterApp
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assumptions
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
    val processHandler = createTerminalProcessHandler(this, javaCommand, console.termSize)
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
    val processHandler = createTerminalProcessHandler(this, javaCommand, console.termSize)
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
    val processHandler = createTerminalProcessHandler(this, javaCommand, console.termSize)
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

  fun `test output auto scrolling`(): Unit = timeoutRunBlockingWithConsole(TermSize(200, 30)) { console ->
    val javaCommand = SimplePrinterApp.NonRuntime.createCommand(SimplePrinterApp.Options("foo", 3))
    val processHandler = createTerminalProcessHandler(this, javaCommand, console.termSize)
    console.attachToProcess(processHandler)
    TestProcessTerminationMessage.attach(processHandler)

    processHandler.startNotify()
    console.awaitOutputEndsWithLines(expectedEndLines = listOf("foo1", "foo2", "foo3", "Input:"))
    awaitAllOutputVisible(console)

    processHandler.writeToStdinAndHitEnter("30 bar")
    console.awaitOutputEndsWithLines(expectedEndLines = listOf("bar28", "bar29", "bar30", "Input:"))
    awaitScrolledToBottom(console)

    processHandler.writeToStdinAndHitEnter(SimplePrinterApp.EXIT)
    console.awaitOutputEndsWithLines(expectedEndLines = listOf(TestProcessTerminationMessage.getMessage(0)))
    processHandler.assertTerminated()
  }

  private suspend fun awaitAllOutputVisible(console: TerminalExecutionConsole) {
    withContext(Dispatchers.UI) {
      Assumptions.assumeTrue(canShowAllOutput(console))
      awaitCondition {
        Assertions.assertThat(canShowAllOutput(console)).isTrue
        val historyLinesCount = console.historyLinesCount
        console.terminalWidget.terminalPanel.verticalScrollModel.value == -historyLinesCount
      }
    }
  }

  private suspend fun awaitScrolledToBottom(console: TerminalExecutionConsole) {
    withContext(Dispatchers.UI) {
      awaitCondition {
        Assertions.assertThat(canShowAllOutput(console)).isFalse
        console.terminalWidget.terminalPanel.verticalScrollModel.value == 0
      }
    }
  }

  private val TerminalExecutionConsole.historyLinesCount: Int
    get() {
      val textBuffer = terminalWidget.terminalTextBuffer
      textBuffer.lock()
      try {
        return textBuffer.historyLinesCount
      }
      finally {
        textBuffer.unlock()
      }
    }

  private fun canShowAllOutput(console: TerminalExecutionConsole): Boolean {
    val textBuffer = console.terminalWidget.terminalTextBuffer
    textBuffer.lock()
    try {
      val cursor = console.terminalWidget.terminal.cursorPosition
      val historyLinesCount = textBuffer.historyLinesCount
      val termHeight = textBuffer.height
      return historyLinesCount + cursor.y <= termHeight
    }
    finally {
      textBuffer.unlock()
    }
  }

  private fun <T> timeoutRunBlockingWithConsole(
    initialSize: TermSize = TermSize(200, 24),
    timeout: Duration = DEFAULT_TEST_TIMEOUT,
    action: suspend CoroutineScope.(TerminalExecutionConsole) -> T,
  ): T = timeoutRunBlocking(timeout) {
    val console = withContext(Dispatchers.UI) {
      TerminalExecutionConsoleBuilder(project).initialTermSize(initialSize).build()
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
