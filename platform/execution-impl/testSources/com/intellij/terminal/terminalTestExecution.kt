// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.process.PtyBasedProcess
import com.intellij.execution.process.SelfKiller
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import com.jediterm.core.util.TermSize
import com.pty4j.windows.conpty.WinConPtyProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assumptions
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal suspend fun createTerminalProcessHandler(
  coroutineScope: CoroutineScope,
  javaCommand: TestJavaMainClassCommand,
  initialTermSize: TermSize,
): ProcessHandler {
  val eelProcess = try {
    javaCommand.createLocalProcessBuilder()
      .interactionOptions(Pty(initialTermSize.columns, initialTermSize.rows, true))
      .scope(coroutineScope)
      .eelIt()
  }
  catch (err: ExecuteProcessException) {
    throw IllegalStateException("Failed to start ${javaCommand.commandLine}", err)
  }
  val javaProcess = eelProcess.convertToJavaProcess()
  assumeTestableProcess(javaProcess)
  return createTerminalProcessHandler(javaProcess, javaCommand.commandLine)
}

/**
 * To have stable tests, we need a reliable VT/ANSI sequences supplier.
 * For Windows, we enforce the use of the bundled ConPTY to maintain predictability.
 */
private fun assumeTestableProcess(localProcess: Process) {
  if (LocalEelDescriptor.osFamily.isWindows) {
    Assertions.assertThat(localProcess).isInstanceOf(WinConPtyProcess::class.java)
    Assumptions.assumeTrue(
      (localProcess as WinConPtyProcess).isBundledConPtyLibrary,
      "Tests require bundled ConPTY to have stable PTY emulation on Windows"
    )
  }
}

private fun createTerminalProcessHandler(process: Process, commandLine: String): KillableProcessHandler {
  val terminalOutputOptions = object : BaseOutputReader.Options() {
    override fun policy(): BaseDataReader.SleepingPolicy = BaseDataReader.SleepingPolicy.BLOCKING
    override fun splitToLines(): Boolean = false
    override fun withSeparators(): Boolean = true
  }
  return object : KillableProcessHandler(process, commandLine, Charsets.UTF_8) {
    override fun readerOptions(): BaseOutputReader.Options = terminalOutputOptions
  }
}

internal val TerminalExecutionConsole.termSize: TermSize
  get() = with(this.terminalWidget.terminalTextBuffer) {
    TermSize(this.width, this.height)
  }

internal fun ProcessHandler.writeToStdinAndHitEnter(input: String) {
  processInput!!.let {
    it.write((input + "\r").toByteArray(Charsets.UTF_8))
    it.flush()
  }
}

internal fun ProcessHandler.assertTerminated() {
  Assertions.assertThat(this.isProcessTerminated).isTrue
}

internal suspend fun ProcessHandler.awaitTerminated(timeout: Duration = 20.seconds) {
  try {
    withTimeout(timeout) {
      doAwaitProcessTerminated(this@awaitTerminated)
    }
    this@awaitTerminated.assertTerminated()
  }
  catch (e: TimeoutCancellationException) {
    System.err.println(e.message)
    this@awaitTerminated.assertTerminated()
    Assertions.fail(e)
  }
}

private suspend fun doAwaitProcessTerminated(processHandler: ProcessHandler) {
  val terminatedDeferred = CompletableDeferred<Unit>()
  val disposable = Disposer.newDisposable()
  terminatedDeferred.invokeOnCompletion { Disposer.dispose(disposable) }
  processHandler.addProcessListener(object : ProcessListener {
    override fun processTerminated(event: ProcessEvent) {
      terminatedDeferred.complete(Unit)
    }
  }, disposable)
  if (processHandler.isProcessTerminated) {
    terminatedDeferred.complete(Unit)
  }
  terminatedDeferred.await()
}

internal class MockPtyBasedProcess(private val withPty: Boolean) : Process(), PtyBasedProcess, SelfKiller {

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

  override fun hasPty(): Boolean = withPty

  override fun setWindowSize(columns: Int, rows: Int) {}

  companion object {
    const val EXIT_CODE = 123
  }
}

internal object TestProcessTerminationMessage {

  private const val PROCESS_TERMINATED_MESSAGE = $$"Process finished with exit code $EXIT_CODE$"

  fun attach(processHandler: ProcessHandler) {
    ProcessTerminatedListener.attach(
      processHandler,
      null /* don't update the status bar */,
      "\n" + PROCESS_TERMINATED_MESSAGE
    )
  }

  fun getMessage(exitCode: Int): String {
    return PROCESS_TERMINATED_MESSAGE.replace($$"$EXIT_CODE$", exitCode.toString())
  }
}
