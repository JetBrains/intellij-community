// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelFileSystemWindowsApi

/**
 * Marker interface that indicates EelApi is running on a local machine.
 * The check for “is local” should be performed only in very specific cases
 */
interface LocalEelApi : EelApi

interface EelApiBase {
  val platform: EelPlatform

  /** Docs: [EelFileSystemApi] */
  val fs: EelFileSystemApi

  /** Docs: [EelExecApi] */
  val exec: EelExecApi

  /** Docs: [EelTunnelsApi] */
  val tunnels: EelTunnelsApi

  val archive: EelArchiveApi

  /**
   * Returns basic info about the user with whose privileges the IJent process runs.
   */
  val userInfo: EelUserInfo

  /**
   * On Unix-like OS, PID is int32. On Windows, PID is uint32. The type of Long covers both PID types, and a separate class doesn't allow
   * to forget that fact and misuse types in APIs.
   */
  interface Pid {
    val value: Long
  }
}

/**
 * Provides access to a local machine or an IJent process running on some machine. An instance of this interface gives ability to run commands
 * on a local or a remote machine.
 * in a Docker container, every call to execute a process (see [EelExecApi]) runs a command in the same Docker container.
 *
 */
interface EelApi : EelApiBase {
  val mapper: EelPathMapper
}

interface EelPosixApi : EelApiBase {
  override val platform: EelPlatform.Posix
  override val tunnels: EelTunnelsPosixApi
  override val userInfo: EelUserPosixInfo
  override val fs: EelFileSystemPosixApi
}

interface EelWindowsApi : EelApiBase {
  override val platform: EelPlatform.Windows
  override val tunnels: EelTunnelsWindowsApi
  override val userInfo: EelUserWindowsInfo
  override val fs: EelFileSystemWindowsApi
}