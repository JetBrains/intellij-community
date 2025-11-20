// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.eelProxyKtHelpers.EelProxy
import com.intellij.platform.eel.getAcceptorForRemotePort
import com.intellij.platform.eel.getConnectionToRemotePort
import com.intellij.platform.eel.hostAddress
import org.jetbrains.annotations.ApiStatus

/**
 * Order the proxy server to accept TCP connection to localhost.
 *
 * [port] may be a specific port or 0 to choose some random port.
 * In case of the random port the actual port can be obtained in [com.intellij.platform.eel.EelProxy.acceptor].
 *
 * See [com.intellij.platform.eel.eelProxy] for example usage.
 *
 * Use [EelTunnelsApi.getAcceptorForRemotePort] directly to specify more options.
 */
@ApiStatus.Experimental
fun EelProxy.acceptOnTcpPort(eel: EelTunnelsApi, host: String = "localhost", port: UShort): EelProxy =
  _acceptorInfo(eel to ("TCP" to (host to port))).acceptorFactory {
    // TODO These EelTunnelsApi.HostAddress.Builder are ugly.
    eel.getAcceptorForRemotePort().hostAddress(EelTunnelsApi.HostAddress.Builder(port).build()).eelIt()
  }

/**
 * Order the proxy server to connect to localhost inside the remote machine.
 *
 * [port] is a specific port to which the proxy server should connect. Setting the port to 0 leads to an error.
 *
 * See [com.intellij.platform.eel.eelProxy] for example usage.
 *
 * Use [EelTunnelsApi.getConnectionToRemotePort] directly to specify more options.
 */
@ApiStatus.Experimental
fun EelProxy.connectToTcpPort(eel: EelTunnelsApi, host: String = "localhost", port: UShort): EelProxy {
  require(port > 0u) { "Port 0 is not allowed" }
  return _connectorInfo(eel to ("TCP" to (host to port))).connectionFactory {
    eel.getConnectionToRemotePort().hostAddress(EelTunnelsApi.HostAddress.Builder(port).build()).eelIt()
  }
}