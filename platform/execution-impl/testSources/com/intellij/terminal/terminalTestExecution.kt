// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.execution.process.*
import com.intellij.platform.eel.EelExecApi.Pty
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.io.BaseDataReader
import com.intellij.util.io.BaseOutputReader
import com.pty4j.windows.conpty.WinConPtyProcess
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CompletableFuture

internal suspend fun createTerminalProcessHandler(javaCommand: TestJavaMainClassCommand): ProcessHandler {
  val eelProcess = try {
    javaCommand.createLocalProcessBuilder()
      .interactionOptions(Pty(80, 25, true))
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
    Assertions.assertInstanceOf(WinConPtyProcess::class.java, localProcess)
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

internal fun ProcessHandler.writeToStdinAndHitEnter(input: String) {
  processInput!!.let {
    it.write((input + "\r").toByteArray(Charsets.UTF_8))
    it.flush()
  }
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
