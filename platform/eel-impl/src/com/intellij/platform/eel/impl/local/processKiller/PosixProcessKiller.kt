// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.processKiller

import com.intellij.execution.process.UnixProcessManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.KillableProcess

internal class PosixProcessKiller(private val process: Process) : KillableProcess {
  init {
    assert(!SystemInfoRt.isWindows)
  }

  override suspend fun interrupt() {
    kill(UnixProcessManager.SIGINT)
  }

  override suspend fun terminate() {
    kill(UnixProcessManager.SIGTERM)
  }

  override suspend fun kill() {
    kill(UnixProcessManager.SIGKILL)
  }

  private fun kill(signal: Int) {
    if (!process.isAlive) return
    UnixProcessManager.sendSignal(process.pid().toInt(), signal)
  }
}