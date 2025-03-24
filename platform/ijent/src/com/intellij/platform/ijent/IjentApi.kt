// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.EelPosixApi
import com.intellij.platform.eel.EelWindowsApi
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import com.intellij.platform.ijent.fs.IjentFileSystemWindowsApi

/**
 * Provides access to an IJent process running on some machine.
 * An instance of this interface gives the ability to run commands on a local or a remote machine. Every instance corresponds to
 * a single machine, i.e., unlike Run Targets, if IJent is launched in a Docker container, every call to execute a process
 * (see [com.intellij.platform.eel.EelExecApi]) runs a command in the same Docker container.
 *
 * Usually, [com.intellij.platform.ijent.deploy] creates instances of [com.intellij.platform.ijent.IjentApi].
 */
sealed interface IjentApi : EelApi, AutoCloseable {

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
  val ijentProcessInfo: IjentProcessInfo

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

  /** Docs: [com.intellij.platform.eel.EelExecApi] */
  override val exec: EelExecApi

  override val fs: IjentFileSystemApi
}

interface IjentPosixApi : IjentApi, EelPosixApi {
  override val fs: IjentFileSystemPosixApi
  override val tunnels: IjentTunnelsPosixApi
}

interface IjentWindowsApi : IjentApi, EelWindowsApi {
  override val fs: IjentFileSystemWindowsApi
  override val tunnels: IjentTunnelsWindowsApi
}