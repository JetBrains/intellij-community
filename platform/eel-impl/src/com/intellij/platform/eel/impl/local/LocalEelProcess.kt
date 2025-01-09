// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.KillableProcess
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.impl.local.processKiller.PosixProcessKiller
import com.intellij.platform.eel.impl.local.processKiller.WinProcessKiller
import com.intellij.platform.eel.provider.utils.asEelChannel
import com.intellij.platform.eel.provider.utils.consumeAsEelChannel
import com.intellij.util.io.awaitExit
import com.pty4j.PtyProcess
import com.pty4j.WinSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.io.IOException

internal class LocalEelProcess private constructor(
  private val process: Process,
  private val resizeWindow: ((WinSize) -> Unit)?,
  private val killer: KillableProcess = if (SystemInfoRt.isWindows) WinProcessKiller(process) else PosixProcessKiller(process),
) : EelProcess, KillableProcess by killer {

  constructor(ptyProcess: PtyProcess) : this(ptyProcess, ptyProcess::setWinSize)
  constructor(process: Process) : this(process, null)

  private val scope: CoroutineScope =
    ApplicationManager.getApplication().service<EelLocalApiService>().scope("LocalEelProcess pid=${process.pid()}")

  override val pid: EelApi.Pid = LocalPid(process.pid())
  override val stdin: EelSendChannel<IOException> = process.outputStream.asEelChannel()
  override val stdout: EelReceiveChannel<IOException> = process.inputStream.consumeAsEelChannel()
  override val stderr: EelReceiveChannel<IOException> = process.errorStream.consumeAsEelChannel()
  override val exitCode: Deferred<Int> = scope.async {
    process.awaitExit()
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

