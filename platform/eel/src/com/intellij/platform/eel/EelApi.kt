// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

/**
 * Marker interface that indicates EelApi is running on a local machine.
 * The check for “is local” should be performed only in very specific cases
 */
interface LocalEelApi : EelApi

/**
 * Provides access to a local machine or an IJent process running on some machine. An instance of this interface gives ability to run commands
 * on a local or a remote machine.
 * in a Docker container, every call to execute a process (see [EelExecApi]) runs a command in the same Docker container.
 *
 */
interface EelApi {
  val platform: EelPlatform


  /** Docs: [EelExecApi] */
  val exec: EelExecApi


  /** Docs: [EelTunnelsApi] */
  val tunnels: EelTunnelsApi

  /**
   * On Unix-like OS, PID is int32. On Windows, PID is uint32. The type of Long covers both PID types, and a separate class doesn't allow
   * to forget that fact and misuse types in APIs.
   */
  interface Pid {
    val value: Long
  }
}

interface EelPosixApi : EelApi {
  override val platform: EelPlatform.Posix
  override val tunnels: EelTunnelsPosixApi
}

interface EelWindowsApi : EelApi {
  override val platform: EelPlatform.Windows
  override val tunnels: EelTunnelsWindowsApi
}