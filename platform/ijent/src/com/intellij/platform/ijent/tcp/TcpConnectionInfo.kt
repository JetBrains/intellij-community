// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

data class TcpConnectionInfo(val connectionInfo: TcpEndpoint, val deployingInfo: TcpDeployInfo)

sealed interface TcpDeployInfo {
  val host: String
  data class RandomPort(override val host: String) : TcpDeployInfo
  data class FixedPort(override val host: String, val port: Int) : TcpDeployInfo {
    constructor(tcpEndpoint: TcpEndpoint) : this(tcpEndpoint.host, tcpEndpoint.port)
  }
  companion object {
    fun localRandom(): RandomPort = TcpDeployInfo.RandomPort("127.0.0.1")
    fun localFixedPort(port: Int): FixedPort = FixedPort("127.0.0.1", port)
    fun listeningFixedPort(port: Int): FixedPort = FixedPort("0.0.0.0", port)
  }
 }