// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.local

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelProcessInfo
import com.intellij.platform.eel.EelProcessManagementPosixApi
import com.intellij.platform.eel.EelProcessManagementWindowsApi
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.provider.LocalEelDescriptor
import kotlinx.coroutines.CompletableDeferred
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

  override suspend fun listProcesses(): List<EelProcessInfo> = localListProcesses()

  override suspend fun processInfo(pid: Long): EelProcessInfo? = localProcessInfo(pid)

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

  override suspend fun listProcesses(): List<EelProcessInfo> = localListProcesses()

  override suspend fun processInfo(pid: Long): EelProcessInfo? = localProcessInfo(pid)

  override suspend fun kill(pid: Long): Boolean = localKill(pid)
}

private fun localListProcesses(): List<EelProcessInfo> =
  ProcessHandle.allProcesses().toList().map { it.toEelProcessInfo() }

private fun localProcessInfo(pid: Long): EelProcessInfo? =
  ProcessHandle.of(pid).map<EelProcessInfo> { it.toEelProcessInfo() }.orElse(null)

private fun localKill(pid: Long): Boolean {
  val handle = ProcessHandle.of(pid).orElse(null) ?: return false
  return handle.destroyForcibly()
}

private fun ProcessHandle.toEelProcessInfo(): LocalEelProcessInfo {
  val info = info()
  // `info()` already fetches the arguments together with everything else, so there is nothing to defer for the local machine.
  val arguments = info.arguments().getOrNull()?.asList() ?: emptyList()
  return LocalEelProcessInfo(
    pid = LocalPid(pid()),
    parentPid = parent().getOrNull()?.let { LocalPid(it.pid()) },
    executable = info.command().getOrNull(),
    arguments = SafeDeferred(CompletableDeferred(arguments)),
    startInstant = info.startInstant().getOrNull(),
    user = info.user().getOrNull(),
  )
}
