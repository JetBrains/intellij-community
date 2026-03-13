// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.execution.process.UnixProcessManager
import com.intellij.execution.process.UnixSignal
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelPosixProcess
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.util.io.awaitExit
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

internal class LocalEelPosixProcess private constructor(
  private val process: Process,
  private val resizeWindow: ((WinSize) -> Unit)?,
  scope: CoroutineScope,
  private val platform: EelPlatform.Posix,
) : EelPosixProcess {
  companion object {
    /**
     * Send signal to the process group. If failed, send it to the process itself.
     * @return false if failed to send signal
     */
    private fun sendSignalToProcessGroup(process: Process, signal: UnixSignal, platform: EelPlatform.Posix): Boolean {
      val code = when (platform) {
        is EelPlatform.Darwin, is EelPlatform.FreeBSD -> signal.darwinCode
        is EelPlatform.Linux -> signal.linuxCode
      }
      val pid = process.pid().toInt()
      var result = UnixProcessManager.sendSignalToGroup(pid, code)
      if (result != 0) {
        logger.warn("Sending $code to group $pid failed: $result")
        result = UnixProcessManager.sendSignal(pid, code)
      }
      if (result != 0) {
        logger.warn("Sending $code to $pid led to error $result")
        return false
      }
      return true
    }

    private val logger = fileLogger()

    @JvmStatic
    suspend fun create(process: Process, resizeWindow: ((WinSize) -> Unit)?, platform: EelPlatform.Posix): LocalEelPosixProcess =
      LocalEelPosixProcess(process, resizeWindow, ApplicationManager.getApplication().serviceAsync<EelLocalApiService>().scope, platform)
  }

  override val pid: EelApi.Pid = LocalPid(process.pid())
  override val stdin: EelSendChannel = process.outputStream.asEelChannel()
  override val stdout: EelReceiveChannel = StreamClosedAwareEelReceiveChannel(process.inputStream.consumeAsEelChannel())
  override val stderr: EelReceiveChannel = StreamClosedAwareEelReceiveChannel(process.errorStream.consumeAsEelChannel())
  override val exitCode: Deferred<Int> = scope.async(CoroutineName("LocalEelPosixProcess pid=${process.pid()}")) {
    process.awaitExit()
  }

  override suspend fun kill() {
    sendSignalToProcessGroup(process, UnixSignal.SIGKILL, platform)
    process.destroyForcibly() // When signal failed, we still need to kill it
  }

  override fun convertToJavaProcess(): Process = process

  override suspend fun resizePty(columns: Int, rows: Int) {
    if (!process.isAlive) {
      throw EelProcess.ResizePtyError.ProcessExited()
    }
    val resizeWindow = this.resizeWindow ?: throw EelProcess.ResizePtyError.NoPty()
    resizeWindow(WinSize(columns, rows))
  }

  override suspend fun interrupt() {
    sendSignalToProcessGroup(process, UnixSignal.SIGINT, platform)
  }

  /**
   * The behavior of this method has one significant difference from [Process.destroy].
   * Although both methods send `SIGTERM` to the process, [Process.destroy] also closes all stdio channels,
   * making it impossible to read data after sending the signal.
   * In contrast, the API user may send and receive data through stdio after invoking [terminate].
   */
  override suspend fun terminate() {
    sendSignalToProcessGroup(process, UnixSignal.SIGTERM, platform)
  }
}

