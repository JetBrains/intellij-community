// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentDeployer")

package com.intellij.platform.ijent

import com.intellij.platform.eel.EelDescriptor

interface IjentSession<T : IjentApi> {
  val isRunning: Boolean
  val remotePathToBinary: String  // TODO Use IjentPath.Absolute.

  suspend fun updateLogLevel()

  fun close()

  fun getIjentInstance(descriptor: EelDescriptor): T

  enum class LogLevel {
    INFO, DEBUG, TRACE
  }
}