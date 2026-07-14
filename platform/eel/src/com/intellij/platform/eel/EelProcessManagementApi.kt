// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus

/**
 * Management of the processes running inside the environment of an [EelExecApi]: listing them, querying a single process by pid, and
 * terminating them.
 *
 * Reach it via [EelExecApi.processManagement]. This is a sealed hierarchy: an instance is either an [EelProcessManagementPosixApi] or
 * an [EelProcessManagementWindowsApi], mirroring [EelExecPosixApi] / [EelExecWindowsApi].
 */
@ApiStatus.Experimental
sealed interface EelProcessManagementApi {
  /** The descriptor of the environment whose processes this API manages. */
  @get:ApiStatus.Experimental
  val descriptor: EelDescriptor

  /**
   * Lists the processes currently running inside the environment.
   *
   * The returned collection is a snapshot; it does not update as processes start or exit.
   *
   * Use it together with [EelProcessInfo.parentPid] to reconstruct the process tree of the environment, e.g. to find and terminate the
   * descendants of a process.
   *
   * When only a single process is of interest, prefer [processInfo]: [listProcesses] enumerates the whole process table, which is an
   * expensive call.
   */
  @ApiStatus.Experimental
  suspend fun listProcesses(): List<EelProcessInfo>

  /**
   * Returns information about the process with the given [pid], or `null` if there is no such process in the environment.
   *
   * Prefer this over filtering [listProcesses] when only a single process is of interest: implementations can query the process
   * directly instead of enumerating the whole process table.
   */
  @ApiStatus.Experimental
  suspend fun processInfo(pid: Long): EelProcessInfo?

  /**
   * Forcibly terminates the process with the given [pid] (e.g. `SIGKILL` on POSIX).
   *
   * @return `true` if the termination request was delivered successfully, `false` otherwise (e.g. the process does not exist).
   */
  @ApiStatus.Experimental
  suspend fun kill(pid: Long): Boolean
}

/**
 * [EelProcessManagementApi] for POSIX environments, which additionally support the graceful termination of a process ([terminate]).
 */
@ApiStatus.Experimental
interface EelProcessManagementPosixApi : EelProcessManagementApi {
  /**
   * Requests a graceful termination of the process with the given [pid] (`SIGTERM`).
   *
   * @return `true` if the termination request was delivered successfully, `false` otherwise (e.g. the process does not exist).
   */
  @ApiStatus.Experimental
  suspend fun terminate(pid: Long): Boolean
}

/**
 * [EelProcessManagementApi] for Windows environments. Windows has no notion of a graceful termination of an arbitrary process by pid
 * (no `SIGTERM`), so only [kill] is available here.
 */
@ApiStatus.Experimental
interface EelProcessManagementWindowsApi : EelProcessManagementApi
