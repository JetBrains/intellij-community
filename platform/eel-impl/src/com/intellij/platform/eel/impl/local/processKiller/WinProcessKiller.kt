// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local.processKiller

import com.intellij.execution.process.LocalProcessService
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.KillableProcess
import com.sun.jna.NativeLibrary
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class WinProcessKiller(private val process: Process) : KillableProcess {
  init {
    assert(SystemInfoRt.isWindows)
  }

  private companion object {
    val exitProcess = lazy { // Might be slow, hence lazy
      NativeLibrary.getInstance("kernel32.dll").getFunction("ExitProcess")
    }
  }

  override suspend fun interrupt() {
    if (!process.isAlive) return
    LocalProcessService.getInstance().sendWinProcessCtrlC(process)
  }

  override suspend fun terminate() {
    if (!process.isAlive) return

    // `ExitProcess` can't be called outside the process, so we create thread inside to call this function
    withContext(Dispatchers.Default) {
      val p = PROCESS_CREATE_THREAD.or(PROCESS_QUERY_INFORMATION).or(PROCESS_VM_OPERATION).or(PROCESS_VM_WRITE).or(PROCESS_VM_READ)
      val openProcess = Kernel32.INSTANCE.OpenProcess(p, false, process.pid().toInt())
      Kernel32.INSTANCE.CreateRemoteThread(openProcess, WinBase.SECURITY_ATTRIBUTES(), 0, exitProcess.value, WinDef.UINT_PTR(0).toPointer(), 0, WinDef.DWORDByReference())
    }
  }

  override suspend fun kill() {
    //  TerminateProcess is called according to JDK sources
    process.destroyForcibly()
  }
}