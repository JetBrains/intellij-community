// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.ijent.fs.IjentFileSystemApi
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to an IJent process running on some machine. An instance of this interface gives ability to run commands
 * on a local or a remote machine. Every instance corresponds to a single machine, i.e. unlike Run Targets, if IJent is launched
 * in a Docker container, every call to execute a process (see [IjentExecApi]) runs a command in the same Docker container.
 *
 * Usually, [IjentSessionProvider] creates instances of [IjentApi].
 */
@ApiStatus.Experimental
interface IjentApi : AutoCloseable {
  val id: IjentId

  val platform: IjentExecFileProvider.SupportedPlatform

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
  val info: Info

  /**
   * Explicitly terminates the process on the remote machine.
   */
  override fun close()

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

  /**
   * [architecture] is the remote architecture of the built binary. Intended to be used for debugging purposes.
   * [remotePid] is a process ID of IJent running on the remote machine.
   * [version] is the version of the IJent binary. Intended to be used for debugging purposes.
   */
  interface Info {
    val architecture: String
    val remotePid: Pid
    val version: String
  }
}
