// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.tcp

import java.net.InetAddress

/**
 * A specification for the IJent deployment specification. Used for the session setup.
 *
 * @param connectionInfo The host:port pair used by IDE to setup a connection with the IJent process.
 *                       **NB: there's a possibility to use bare TCP to connect to the remote host. If it's not needed it could be omitted just to the port**
 * @param deployingInfo The host:port pair or just host that is used to launch the IJent process. The host is the listening address. If the port is not specified,
 *                       a random port will be chosen.
 */
data class TcpIjentDeploymentAndConnectionSpec(val connectionInfo: TcpDeployInfo.FixedPort, val deployingInfo: TcpDeployInfo)

sealed interface TcpDeployInfo {
  val host: String
  data class RandomPort(override val host: String) : TcpDeployInfo
  data class FixedPort(override val host: String, val port: Int) : TcpDeployInfo
  companion object {
    private val loopbackAddress = InetAddress.getLoopbackAddress().hostAddress
    fun localRandom(): RandomPort = RandomPort(loopbackAddress)
    fun localFixedPort(port: Int): FixedPort = FixedPort(loopbackAddress, port)
    fun listeningFixedPort(port: Int): FixedPort = FixedPort("0.0.0.0", port)
  }
 }