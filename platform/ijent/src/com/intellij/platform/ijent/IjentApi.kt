// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.platform.ijent.fs.IjentFileSystemWindowsApi

/**
 * Provides access to an IJent process running on some machine. An instance of this interface gives ability to run commands
 * on a local or a remote machine. Every instance corresponds to a single machine, i.e. unlike Run Targets, if IJent is launched
 * in a Docker container, every call to execute a process (see [IjentExecApi]) runs a command in the same Docker container.
 *
 * Usually, [com.intellij.platform.ijent.deploy] creates instances of [IjentApi].
 */
sealed interface IjentApi : AutoCloseable {
  val platform: IjentPlatform

  /**
   * Checks if the API is active and is safe to use. If it returns false, IJent on the other side is certainly unavailable.
   * If it returns true, it's likely available.
   *
   * The property must return true as soon as [close] is called.
   *
   * The property must not perform any blocking operation and must work fast.
   */
  val isRunning: Boolean

  /**
   * Returns basic info about the process that doesn't change during the lifetime of the process.
   */
  val info: IjentInfo

  /**
   * Explicitly terminates the process on the remote machine.
   *
   * The method is not supposed to block the current thread.
   *
   * For awaiting, use [waitUntilExit].
   */
  override fun close()

  /**
   * Suspends until the IJent process on the remote side terminates.
   * This method doesn't throw exceptions.
   */
  suspend fun waitUntilExit()

  /** Docs: [IjentExecApi] */
  val exec: IjentExecApi

  val fs: IjentFileSystemApi

  /** Docs: [IjentTunnelsApi] */
  val tunnels: IjentTunnelsApi

  /**
   * On Unix-like OS, PID is int32. On Windows, PID is uint32. The type of Long covers both PID types, and a separate class doesn't allow
   * to forget that fact and misuse types in APIs.
   */
  interface Pid {
    val value: Long
  }
}

interface IjentPosixApi : IjentApi {
  override val info: IjentPosixInfo
  override val fs: IjentFileSystemPosixApi
  override val tunnels: IjentTunnelsPosixApi
}

interface IjentWindowsApi : IjentApi {
  override val info: IjentWindowsInfo
  override val fs: IjentFileSystemWindowsApi
  override val tunnels: IjentTunnelsWindowsApi
}