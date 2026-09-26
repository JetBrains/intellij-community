// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelProcessInfo
import com.intellij.platform.eel.EelProcessManagementPosixApi
import com.intellij.platform.eel.EelProcessManagementWindowsApi
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.util.system.WindowsProcessInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

internal class LocalEelProcessInfo(
  override val pid: EelApi.Pid,
  override val parentPid: EelApi.Pid?,
  override val executable: String?,
  override val arguments: SafeDeferred<List<String>>,
  override val startInstant: Instant?,
  override val user: String?,
) : EelProcessInfo {
  override fun toString(): String =
    "LocalEelProcessInfo(pid=${pid.value}, parentPid=${parentPid?.value}, executable=$executable)"
}

/**
 * `EelProcessManagementApi` for the local (IDE host) POSIX machine, backed by `java.lang.ProcessHandle`.
 */
internal object LocalEelPosixProcessManagementApi : EelProcessManagementPosixApi {
  override val descriptor: EelDescriptor = LocalEelDescriptor

  override suspend fun listProcesses(): List<EelProcessInfo> = localListProcesses(null)

  override suspend fun processInfo(pid: Long): EelProcessInfo? = localProcessInfo(pid, null)

  override suspend fun terminate(pid: Long): Boolean {
    val handle = ProcessHandle.of(pid).orElse(null) ?: return false
    return handle.destroy()
  }

  override suspend fun kill(pid: Long): Boolean = localKill(pid)
}

/**
 * `EelProcessManagementApi` for the local (IDE host) Windows machine, backed by `java.lang.ProcessHandle`.
 */
internal object LocalEelWindowsProcessManagementApi : EelProcessManagementWindowsApi {
  override val descriptor: EelDescriptor = LocalEelDescriptor

  override suspend fun listProcesses(): List<EelProcessInfo> =
    withContext(Dispatchers.IO) {
      localListProcesses { getWindowsArgsAndPpid(it) }
    }

  override suspend fun processInfo(pid: Long): EelProcessInfo? =
    withContext(Dispatchers.IO) {
      localProcessInfo(pid, getWindowsArgsAndPpid(pid))
    }

  override suspend fun kill(pid: Long): Boolean = localKill(pid)
}

private fun localListProcesses(argAndPPidObtaner: ((pid: Long) -> ArgsAndPPid?)?): List<LocalEelProcessInfo> =
  ProcessHandle.allProcesses().toList().map { it.toEelProcessInfo(argAndPPidObtaner?.invoke(it.pid())) }

private fun localProcessInfo(pid: Long, argumentsAndPPid: ArgsAndPPid?): LocalEelProcessInfo? =
  ProcessHandle.of(pid).map { it.toEelProcessInfo(argumentsAndPPid) }.orElse(null)

private fun localKill(pid: Long): Boolean {
  val handle = ProcessHandle.of(pid).orElse(null) ?: return false
  return handle.destroyForcibly()
}

private fun ProcessHandle.toEelProcessInfo(argumentsAndPPid: ArgsAndPPid?): LocalEelProcessInfo {
  val info = info()
  // `info()` already fetches the arguments together with everything else, so there is nothing to defer for the local machine.
  val arguments = info.arguments().getOrNull()?.asList() ?: emptyList()
  return LocalEelProcessInfo(
    pid = LocalPid(pid()),
    parentPid = (parent().getOrNull()?.pid() ?: argumentsAndPPid?.parentId)?.let { ppid -> LocalPid(ppid) },
    executable = info.command().getOrNull(),
    arguments = SafeDeferred(CompletableDeferred(argumentsAndPPid?.args ?: arguments)),
    startInstant = info.startInstant().getOrNull(),
    user = info.user().getOrNull(),
  )
}

private class ArgsAndPPid(val args: List<String>, val parentId: Long?)

/**
 * Adds the data that `java.lang.ProcessHandle` misses on Windows: the parent pid and the arguments.
 * Win32 has no public API for them, so the JVM reports neither. See IJPL-255154 and JDK-8263139.
 *
 *
 * The native API cannot read a protected process, a service, or a process of another user. The caller must still see
 * such a process in the list, because [LocalEelWindowsProcessManagementApi.kill] can still kill it.
 *
 * @return this info with the native data, or this info unchanged when the native API cannot read the process.
 */
private fun getWindowsArgsAndPpid(pid: Long): ArgsAndPPid? {
  val nativeInfo = WindowsProcessInfo.get(pid).getOrElse { err ->
    LOG.debug(err) { "Cannot read the process $pid with the native API" }
    return null
  }
  // The first element of an argument vector is the executable, so drop it: `EelProcessInfo.arguments` holds the
  // arguments alone, as `ProcessHandle.Info.arguments()` does on POSIX.
  val nativeArguments = nativeInfo.arguments.drop(1)
  return ArgsAndPPid(args = nativeArguments, parentId = nativeInfo.parentId)
}

private val LOG = logger<LocalEelProcessInfo>()
