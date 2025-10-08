// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

data class TcpConnectionInfo(
  val remoteHost: String,
  val remotePort: Int,
  val localPort: Int
) {
  companion object {
    fun createLocalConfiguration(port: Int): TcpConnectionInfo = TcpConnectionInfo("localhost", port, port)
  }
}