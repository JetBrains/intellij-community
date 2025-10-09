// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.diagnostic.ThreadDumper
import com.intellij.execution.process.*
import com.intellij.openapi.application.UI
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.model.TerminalTextBuffer
import kotlinx.coroutines.*
import java.io.InputStream
import java.io.OutputStream
import java.lang.management.ThreadInfo
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TerminalExecutionConsoleTest : BasePlatformTestCase() {

  override fun runInDispatchThread(): Boolean = false

  fun `test disposing console should stop emulator thread`(): Unit = timeoutRunBlocking(20.seconds) {
    val processHandler = NopProcessHandler()
    val console = withContext(Dispatchers.UI) {
      TerminalExecutionConsole(project, null)
    }
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    awaitCondition(5.seconds) { findEmulatorThreadInfo() != null }
    assertNotNull(findEmulatorThreadInfo())
    withContext(Dispatchers.UI) {
      Disposer.dispose(console)
    }
    awaitCondition(5.seconds) { findEmulatorThreadInfo() == null }
    assertNull(findEmulatorThreadInfo())
  }

  private suspend fun awaitCondition(timeout: Duration, condition: () -> Boolean) {
    withTimeoutOrNull(timeout) {
      while (!condition()) {
        delay(100.milliseconds)
      }
    }
  }

  private fun findEmulatorThreadInfo(): ThreadInfo? {
    val threadInfos = ThreadDumper.getThreadInfos()
    return threadInfos.find { it.threadName.startsWith("TerminalEmulator-") }
  }

  fun `test support ColoredProcessHandler`(): Unit = timeoutRunBlockingWithConsole { console ->
    val processHandler = ColoredProcessHandler(MockPtyBasedProcess, "my command line", Charsets.UTF_8)
    assertTrue(TerminalExecutionConsole.isAcceptable(processHandler))
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    processHandler.notifyTextAvailable("\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[32mFoo\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.setShouldDestroyProcessRecursively(false)
    ProcessTerminatedListener.attach(processHandler, project, $$"Process finished with exit code $EXIT_CODE$")
    processHandler.destroyProcess()
    val terminalWidget = console.terminalWidget
    awaitCondition(5.seconds) {
      ScreenText.collect(terminalWidget.terminalTextBuffer).contains(MockPtyBasedProcess.EXIT_CODE.toString())
    }
    assertTrue(terminalWidget.text.startsWith("my command line\nFoo"))
    val screenText = ScreenText.collect(terminalWidget.terminalTextBuffer)
    assertTrue(screenText.contains(Chunk("Foo", TextStyle(TerminalColor(2), null))))
  }

  fun `test support OSProcessHandler`(): Unit = timeoutRunBlockingWithConsole { console ->
    val processHandler = OSProcessHandler(MockPtyBasedProcess, "command line", Charsets.UTF_8)
    assertTrue(TerminalExecutionConsole.isAcceptable(processHandler))
    console.attachToProcess(processHandler)
    processHandler.startNotify()
    processHandler.notifyTextAvailable("\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[32mFoo\u001b[0m", ProcessOutputTypes.STDOUT)
    processHandler.notifyTextAvailable("\u001b[43mBar\u001b[0m", ProcessOutputTypes.STDOUT)
    val terminalWidget = console.terminalWidget
    awaitCondition(5.seconds) {
      ScreenText.collect(terminalWidget.terminalTextBuffer).contains("Bar")
    }
    assertTrue(terminalWidget.text.startsWith("command line\nFooBar"))
    val screenText = ScreenText.collect(terminalWidget.terminalTextBuffer)
    assertTrue(screenText.contains(Chunk("Foo", TextStyle(TerminalColor(2), null))))
    assertTrue(screenText.contains(Chunk("Bar", TextStyle(null, TerminalColor(3)))))
  }

  fun <T> timeoutRunBlockingWithConsole(
    timeout: Duration = 20.seconds,
    action: suspend CoroutineScope.(TerminalExecutionConsole) -> T,
  ): T = timeoutRunBlocking(timeout) {
    val console = withContext(Dispatchers.UI) {
      TerminalExecutionConsole(project, null)
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


internal class Chunk(val text: String, val style: TextStyle)

internal class ScreenText(val chunks: List<Chunk>) {

  fun contains(text: String): Boolean = chunks.any { it.text.contains(text) }

  fun contains(chunksToFind: Chunk): Boolean = chunks.any {
    it.text == chunksToFind.text && it.style == chunksToFind.style
  }

  companion object {
    fun collect(textBuffer: TerminalTextBuffer): ScreenText {
      val result: List<Chunk> = textBuffer.screenLinesStorage.flatMap {
        it.entries.map { entry ->
          Chunk(entry.text.toString(), entry.style)
        }
      }
      return ScreenText(result)
    }
  }
}

internal object MockPtyBasedProcess : Process(), PtyBasedProcess {

  const val EXIT_CODE = 123

  private val exitCodeFuture: CompletableFuture<Int> = CompletableFuture()

  override fun destroy() {
    exitCodeFuture.complete(EXIT_CODE)
  }

  override fun waitFor(): Int = exitCodeFuture.get()

  override fun exitValue(): Int {
    return exitCodeFuture.getNow(null) ?: throw IllegalThreadStateException()
  }

  override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
  override fun getErrorStream(): InputStream = InputStream.nullInputStream()
  override fun getInputStream(): InputStream = InputStream.nullInputStream()

  override fun hasPty(): Boolean = true

  override fun setWindowSize(columns: Int, rows: Int) {}
}
