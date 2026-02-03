// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.window.to.foreground

import com.intellij.openapi.util.*
import com.jetbrains.rd.util.getLogger
import com.jetbrains.rd.util.trace
import org.jetbrains.annotations.ApiStatus
import kotlin.jvm.optionals.getOrNull

private val logger = getLogger<BringProcessWindowToForegroundSupport>()

private val terminalPIDKey = Key<UInt?>("ProcessWindowUtils_TerminalPIDKey")
private val terminalBroughtSuccessfullyKey = Key<Boolean>("ProcessWindowUtils_TerminalBroughtSuccessfullyKey")

@ApiStatus.Internal
fun BringProcessWindowToForegroundSupport.bring(pid: UInt, dataHolder: UserDataHolderEx, tryExternalTerminalApp: Boolean) : Boolean {
  if (!this.isApplicable())
    return false

  if (bring(pid)) {
    logger.trace { "Could successfully bring $pid process into foreground" }
    return true
  }

  if (!tryExternalTerminalApp) return false

  logger.trace { "Bringing terminal window into foreground if it exists" }

  return tryBringTerminalWindow(dataHolder, pid)
    .also { logger.trace { "Bringing cmd process to foreground : ${if (it) "succeeded" else "failed"}" } }
}

private fun BringProcessWindowToForegroundSupport.tryBringTerminalWindow(dataHolder: UserDataHolderEx, pid: UInt): Boolean {
  if (dataHolder.getUserData(terminalBroughtSuccessfullyKey) == false)
    return false

  val result = if (SystemInfo.isWindows)
  // on windows WindowsTerminal.exe process is not a parent of the debuggee, so we have to find the terminal windows associated with the debuggee first
    return (this as WinBringProcessWindowToForegroundSupport).tryBringWindowsTerminalInForeground(dataHolder, pid)
  else
    when (val terminalPid = dataHolder.getOrMaybeCreateUserData(terminalPIDKey) {
      (tryFindParentProcess(pid, listOf("MacOS/Terminal", "gnome-terminal")) ?: run {
        logger.trace { "Could find neither main window of $pid process, nor parent cmd process. Exiting" };
        return@getOrMaybeCreateUserData null
      }
      ).pid().toUInt()
    }) {
      null -> false
      else -> bring(terminalPid)
    }

  return result.also { dataHolder.putUserDataIfAbsent(terminalBroughtSuccessfullyKey, it) }
}

private fun tryFindParentProcess(pid: UInt, parentProcessesWeLookingFor: List<String>): ProcessHandle? {
  val pidAsLong = pid.toLong()
  val debuggeeProcess = ProcessHandle.allProcesses().filter { it.pid() == pidAsLong }.findFirst().getOrNull()
                        ?: run { logger.trace { "Can't find the process with pid $pid" }; return null }

  val ideProcess = ProcessHandle.current()

  var parentProcess = debuggeeProcess.parent().getOrNull()

  while (parentProcess != null && parentProcess != ideProcess) {
    val command = parentProcess.info().command().getOrNull()
    if (command != null && parentProcessesWeLookingFor.any { command.contains(it) })
      return parentProcess

    parentProcess = parentProcess.parent().getOrNull()
  }

  return null
}

private fun WinBringProcessWindowToForegroundSupport.tryBringWindowsTerminalInForeground(dataHolder: UserDataHolder, pid: UInt): Boolean {
  if (tryFindParentProcess(pid, listOf("JetBrains.Debugger.Worker.exe")) == null) {
    logger.trace { "The process hasn't been launched under JetBrains.Debugger.Worker.exe" }
    return false
  }

  // On windows only 1 instance of terminal can be launched
  val windowsTerminalPid = dataHolder.getOrCreateUserDataUnsafe(terminalPIDKey) {
    ProcessHandle.allProcesses()
      .filter {
        val command = it.info().command().getOrNull() ?: return@filter false
        command.contains("Program Files\\WindowsApps\\Microsoft.WindowsTerminal") && command.endsWith("WindowsTerminal.exe")
      }
      .findFirst()
      .getOrNull()
      ?.pid()
      ?.toUInt()
  } ?: return false

  // if there are more than 1 Debugger.Worker.exe window, we will bring none of them
  return bringWindowWithName(windowsTerminalPid, dataHolder, "Debugger.Worker.exe")
}

@ApiStatus.Internal
fun BringProcessWindowToForegroundSupport.isApplicable() =
  ((this as? BringProcessWindowToForegroundSupportApplicable)?.isApplicable() ?: true)