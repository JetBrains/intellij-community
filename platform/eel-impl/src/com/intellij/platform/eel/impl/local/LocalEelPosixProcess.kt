// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.execution.process.UnixProcessManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.eel.EelApi
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
) : EelPosixProcess {
  companion object {
    @JvmStatic
    suspend fun create(process: Process, resizeWindow: ((WinSize) -> Unit)?): LocalEelPosixProcess =
      LocalEelPosixProcess(process, resizeWindow, ApplicationManager.getApplication().serviceAsync<EelLocalApiService>().scope)
  }

  override val pid: EelApi.Pid = LocalPid(process.pid())
  override val stdin: EelSendChannel = process.outputStream.asEelChannel()
  override val stdout: EelReceiveChannel = StreamClosedAwareEelReceiveChannel(process.inputStream.consumeAsEelChannel())
  override val stderr: EelReceiveChannel = StreamClosedAwareEelReceiveChannel(process.errorStream.consumeAsEelChannel())
  override val exitCode: Deferred<Int> = scope.async(CoroutineName("LocalEelPosixProcess pid=${process.pid()}")) {
    process.awaitExit()
  }

  override suspend fun kill() {
    process.destroyForcibly()
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
    UnixProcessManager.sendSignal(process.pid().toInt(), UnixProcessManager.SIGINT)
  }

  /**
   * The behavior of this method has one significant difference from [Process.destroy].
   * Although both methods send `SIGTERM` to the process, [Process.destroy] also closes all stdio channels,
   * making it impossible to read data after sending the signal.
   * In contrast, the API user may send and receive data through stdio after invoking [terminate].
   */
  override suspend fun terminate() {
    UnixProcessManager.sendSignal(process.pid().toInt(), UnixProcessManager.SIGTERM)
  }
}

