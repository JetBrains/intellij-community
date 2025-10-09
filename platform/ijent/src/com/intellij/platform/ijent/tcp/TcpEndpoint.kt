// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

data class TcpEndpoint(val host: String, val port: Int) {
  companion object {
    fun createLocalConfiguration(port: Int): TcpEndpoint = TcpEndpoint("127.0.0.1", port)
  }
}