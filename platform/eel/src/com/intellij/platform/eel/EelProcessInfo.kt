// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import java.time.Instant

/**
 * A snapshot of a process running inside the environment of an [EelExecApi].
 *
 * It describes a process running in the Eel environment (which may be WSL, a Docker container, a remote machine, …) instead of the
 * IDE host.
 *
 * Obtain instances via [EelProcessManagementApi.listProcesses] or [EelProcessManagementApi.processInfo].
 */
@ApiStatus.Experimental
interface EelProcessInfo {
  /** The identifier of the process in the environment. */
  val pid: EelApi.Pid

  /** The identifier of the parent process in the environment, or `null` when it is unknown or the process has no parent. */
  val parentPid: EelApi.Pid?

  /** The executable path or name of the process, or `null` when it cannot be determined. */
  val executable: String?

  /**
   * The command line arguments of the process, not including the executable itself.
   *
   * Fetching the arguments can be expensive in some environments (notably Windows), so it is computed lazily: neither
   * [EelProcessManagementApi.listProcesses] nor [EelProcessManagementApi.processInfo] fetches them up front. The first
   * [await][SafeDeferred.await] loads them on demand. The result may be empty when the arguments cannot be determined.
   */
  val arguments: SafeDeferred<List<String>>

  /** The start time of the process, or `null` when it cannot be determined. */
  val startInstant: Instant?

  /** The user that owns the process, or `null` when it cannot be determined. */
  val user: String?
}
