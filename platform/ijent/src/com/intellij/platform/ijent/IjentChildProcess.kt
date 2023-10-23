// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

interface IjentChildProcess {
  val pid: Int
  val stdin: SendChannel<ByteArray>
  val stdout: ReceiveChannel<ByteArray>
  val stderr: ReceiveChannel<ByteArray>
  val exitCode: Deferred<Int>

  @Deprecated("Switch either to kill or terminate")
  suspend fun sendSignal(signal: Int)

  suspend fun terminate()
  suspend fun kill()
}