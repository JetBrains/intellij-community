// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

data class TcpConnectionInfo(
  /**
   * The address that used by remote agent for port listening
   *
   * In the common case, it's `127.0.0.1`, but Docker requires `0.0.0.0`
   */
  val ijentBindAddress: String,
  /**
   * Address to connect to the remote agent
   */
  val remoteHost: String,
  /**
   * Port that remote agent is listening to on.
   */
  val ijentListeningPort: Int,
  /**
   * Port that local agent is connecting to.
   *
   * It could be a different port than [ijentListeningPort] if the port is forwarded (e.g., docker, ssh)
   */
  val localPort: Int
) {
  companion object {
    fun createLocalConfiguration(port: Int): TcpConnectionInfo = TcpConnectionInfo("127.0.0.1", "127.0.0.1", port, port)
    fun createDockerConfiguration(ijentListeningPort: Int, localPort: Int): TcpConnectionInfo = TcpConnectionInfo("0.0.0.0", "127.0.0.1", ijentListeningPort, localPort)
  }
}