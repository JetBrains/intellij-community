// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.tcp.TcpEelMachine
import com.intellij.platform.ijent.tcp.IjentIsolatedTcpDeployingStrategy
import kotlinx.coroutines.CoroutineScope

class RawTcpEelMachine(
  val host: String,
  private val coroutineScope: CoroutineScope,
) : TcpEelMachine("tcp-$host") {
  override fun createStrategy(): IjentIsolatedTcpDeployingStrategy {
    TODO("not implemented")
  }
}