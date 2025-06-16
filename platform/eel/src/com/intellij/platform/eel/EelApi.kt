// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi
import org.jetbrains.annotations.ApiStatus

/**
 * Marker interface that indicates EelApi is running on a local machine.
 * The check for “is local” should be performed only in very specific cases
 */
@ApiStatus.Experimental
interface LocalEelApi : EelApi

@ApiStatus.Experimental
interface EelApi {
  @get:ApiStatus.Experimental
  val descriptor: EelDescriptor

  // TODO: should it be extension property?
  val platform: EelPlatform

  /** Docs: [EelFileSystemApi] */
  @get:ApiStatus.Internal
  val fs: EelFileSystemApi

  /** Docs: [EelExecApi] */
  @get:ApiStatus.Experimental
  val exec: EelExecApi

  /** Docs: [EelTunnelsApi] */
  @get:ApiStatus.Experimental
  val tunnels: EelTunnelsApi

  @get:ApiStatus.Internal
  val archive: EelArchiveApi

  /**
   * Returns basic info about the user with whose privileges the IJent process runs.
   */
  @get:ApiStatus.Experimental
  val userInfo: EelUserInfo

  /**
   * On Unix-like OS, PID is int32. On Windows, PID is uint32. The type of Long covers both PID types, and a separate class doesn't allow
   * to forget that fact and misuse types in APIs.
   */
  @ApiStatus.Experimental
  interface Pid {
    val value: Long
  }
}

@ApiStatus.Experimental
interface EelPosixApi : EelApi {
  override val exec: EelExecPosixApi
  override val platform: EelPlatform.Posix
  override val tunnels: EelTunnelsPosixApi
  override val userInfo: EelUserPosixInfo

  @get:ApiStatus.Internal
  override val fs: EelFileSystemPosixApi
}

@ApiStatus.Experimental
interface EelWindowsApi : EelApi {
  override val exec: EelExecWindowsApi
  override val platform: EelPlatform.Windows
  override val tunnels: EelTunnelsWindowsApi
  override val userInfo: EelUserWindowsInfo

  @get:ApiStatus.Internal
  override val fs: EelFileSystemWindowsApi
}