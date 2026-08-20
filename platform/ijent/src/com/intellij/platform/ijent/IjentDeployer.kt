// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentDeployer")

package com.intellij.platform.ijent

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelPlatform
import kotlinx.coroutines.DelicateCoroutinesApi

interface IjentMachine : EelMachine {
  fun getCachedIjentSession(): IjentSession?
  suspend fun getIjentSession(sessionScope: ParentOfIjentScopes): IjentSession

  /** `false` when the backend is known-gone and callers should skip remote operations that would otherwise spin up a session. */
  fun isBackendAvailable(): Boolean = true
}

interface IjentSession {
  val isRunning: Boolean
  val platform: EelPlatform
  val remotePathToBinary: String  // TODO Use IjentPath.Absolute.

  /**
   * A scope for the process. Use very cautiously, it's not a supervisor scope, it can destroy the whole communication.
   */
  @DelicateCoroutinesApi
  val sessionCoroutineScope: IjentScope

  suspend fun updateLogLevel()

  /**
   * Points IJent's parent-death watch at [pid] — the process that owns this session's lifetime. When that process
   * dies, IJent loses the right to outlive its disconnects and exits with its last client. Calling again replaces
   * the watch and restores the privilege, which is how a relaunched owner takes the session back.
   */
  suspend fun setParentProcessToWatch(pid: Long)

  fun close()

  fun getIjentInstance(descriptor: EelDescriptor): IjentApi

  val eventBus: IjentEventBus

  enum class LogLevel {
    INFO, DEBUG, TRACE
  }

  interface Posix : IjentSession {
    override fun getIjentInstance(descriptor: EelDescriptor): IjentPosixApi
  }

  interface Windows : IjentSession {
    override fun getIjentInstance(descriptor: EelDescriptor): IjentWindowsApi
  }
}