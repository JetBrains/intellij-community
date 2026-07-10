// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface indicating that this [EelApi] targets the local machine — the one the IDE process runs on.
 *
 * Checking for locality should be rare: most code should treat the local and remote environments uniformly through [EelApi], so that
 * it keeps working in WSL and Dev Container projects. Reach for this only in the few cases that genuinely require a local-only shortcut.
 */
@ApiStatus.Experimental
interface LocalEelApi : EelApi

/**
 * The entry point of the Eel API: a live handle to a single execution environment — the local machine, a WSL distribution,
 * a Docker container, or a Dev Container.
 *
 * Unlike [EelDescriptor], which only *describes* how to reach an environment, an [EelApi] represents a *running, connected* one.
 * Obtain it from a descriptor with `descriptor.toEelApi()` (suspending) or `descriptor.toEelApiBlocking()`; the descriptor itself comes
 * from `project.getEelDescriptor()` or `path.getEelDescriptor()`. Establishing the connection may start, deploy, or connect to an
 * IntelliJ Agent and can fail with [EelUnavailableException].
 *
 * Every operation reached through an [EelApi] runs *inside that environment*, not on the IDE host. A single instance exposes:
 * - [fs] — a view of the environment's file system (also reachable through NIO `Path` operations on routed paths);
 * - [exec] — process execution inside the environment (use it instead of `ProcessBuilder`);
 * - [tunnels] — TCP and Unix-socket tunnels into and out of the environment;
 * - [platform] and [userInfo] — OS, architecture, and user info; use [platform] instead of host-side `SystemInfo`.
 *
 * Most operations are `suspend` functions and must be called from a coroutine.
 *
 * [EelPosixApi] and [EelWindowsApi] are the OS-family-specific refinements that narrow the capabilities and info to a known [EelOsFamily].
 */
@ApiStatus.Experimental
interface EelApi {
  /**
   * The [EelDescriptor] this instance was obtained from, i.e. the route to this environment.
   */
  @get:ApiStatus.Experimental
  val descriptor: EelDescriptor

  /**
   * OS family and architecture of this environment. Prefer this over host-side `SystemInfo` when choosing environment-specific
   * binaries or branching on the OS.
   */
  val platform: EelPlatform

  /** A view of the environment's file system. See [EelFileSystemApi]. */
  @get:ApiStatus.Internal
  val fs: EelFileSystemApi

  /** Process execution inside the environment. See [EelExecApi]. */
  @get:ApiStatus.Experimental
  val exec: EelExecApi

  /** TCP and Unix-socket tunnels for the environment. See [EelTunnelsApi]. */
  @get:ApiStatus.Experimental
  val tunnels: EelTunnelsApi

  /** Extraction of archives within the environment. See [EelArchiveApi]. */
  @get:ApiStatus.Internal
  val archive: EelArchiveApi

  /**
   * Basic information about the user that operations in this environment run as.
   */
  @get:ApiStatus.Experimental
  val userInfo: EelUserInfo

  /**
   * A process identifier in this environment.
   *
   * On Unix-like OSes a PID is an int32; on Windows it is a uint32. [Long] covers both ranges, and wrapping it in a dedicated type
   * keeps that distinction visible and prevents misusing raw numbers across APIs.
   */
  @ApiStatus.Experimental
  interface Pid {
    val value: Long
  }
}

/**
 * [EelApi] for a POSIX environment (Linux, macOS, FreeBSD, …). Narrows each capability and info type to its POSIX variant.
 */
@ApiStatus.Experimental
interface EelPosixApi : EelApi {
  override val exec: EelExecPosixApi
  override val platform: EelPlatform.Posix
  override val tunnels: EelTunnelsPosixApi
  override val userInfo: EelUserPosixInfo

  @get:ApiStatus.Internal
  override val fs: EelFileSystemPosixApi
}

/**
 * [EelApi] for a Windows environment. Narrows each capability and info type to its Windows variant.
 */
@ApiStatus.Experimental
interface EelWindowsApi : EelApi {
  override val exec: EelExecWindowsApi
  override val platform: EelPlatform.Windows
  override val tunnels: EelTunnelsWindowsApi
  override val userInfo: EelUserWindowsInfo

  @get:ApiStatus.Internal
  override val fs: EelFileSystemWindowsApi
}