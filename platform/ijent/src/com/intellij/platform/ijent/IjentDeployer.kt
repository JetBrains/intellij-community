// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentDeployer")

package com.intellij.platform.ijent

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform

interface IjentSession {
  val isRunning: Boolean
  val platform: EelPlatform
  val remotePathToBinary: String  // TODO Use IjentPath.Absolute.

  suspend fun updateLogLevel()

  fun close()

  fun getIjentInstance(descriptor: EelDescriptor): IjentApi

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