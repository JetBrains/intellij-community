// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.execution.process.LocalProcessService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.EelWindowsProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.util.io.awaitExit
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.io.IOException

internal class LocalEelWindowsProcess(
  private val process: Process,
  private val resizeWindow: ((WinSize) -> Unit)?,
) : EelWindowsProcess {

  private val scope: CoroutineScope =
    ApplicationManager.getApplication().service<EelLocalApiService>().scope("LocalEelProcess pid=${process.pid()}")

  override val pid: EelApi.Pid = LocalPid(process.pid())
  override val stdin: EelSendChannel<IOException> = process.outputStream.asEelChannel()
  override val stdout: EelReceiveChannel<IOException> = StreamClosedAwareEelReceiveChannel(process.inputStream.consumeAsEelChannel())
  override val stderr: EelReceiveChannel<IOException> = StreamClosedAwareEelReceiveChannel(process.errorStream.consumeAsEelChannel())
  override val exitCode: Deferred<Int> = scope.async {
    process.awaitExit()
  }

  override suspend fun kill() {
    process.destroyForcibly()
  }

  override suspend fun interrupt() {
    LocalProcessService.getInstance().sendWinProcessCtrlC(process)
  }

  override fun convertToJavaProcess(): Process = process

  override suspend fun resizePty(columns: Int, rows: Int) {
    if (!process.isAlive) {
      throw EelProcess.ResizePtyError.ProcessExited()
    }
    val resizeWindow = this.resizeWindow ?: throw EelProcess.ResizePtyError.NoPty()
    resizeWindow(WinSize(columns, rows))
  }
}

