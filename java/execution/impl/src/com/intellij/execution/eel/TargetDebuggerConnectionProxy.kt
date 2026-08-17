// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelProxy
import com.intellij.platform.eel.channels.EelDelicateApi
import com.intellij.platform.eel.eelProxy
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.portAccessibleLocally.EelPortAccessibleLocally.Companion.isEelPortAccessibleLocally
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.acceptOnTcpPort
import com.intellij.platform.eel.provider.utils.connectToTcpPort
import com.intellij.util.net.NetUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ThreadLocalRandom

private val LOG = logger<TargetDebuggerConnectionProxy>()

internal data class Mapping(val local: Int, val remote: Int)

internal object TargetDebuggerConnectionProxy {

  @Service(Service.Level.PROJECT)
  private class ProxyCoroutineScopeHolder(val coroutineScope: CoroutineScope)

  private val Project.proxyCoroutineScope: CoroutineScope
    get() = service<ProxyCoroutineScopeHolder>().coroutineScope

  fun getProxy(project: Project, forRemoteServer: Boolean, disposable: Disposable): Mapping = runBlockingMaybeCancellable {
    project.getDirectPort() ?: project.runTunnel(forRemoteServer, disposable)
  }

  // a special case for WSL with a mirrored network mode
  private suspend fun Project.getDirectPort(): Mapping? {
    val localPort = NetUtils.findAvailableSocketPort()
    val localPortUShort = localPort.toUShort()
    val eelDescriptor = getEelDescriptor()
    if (eelDescriptor == LocalEelDescriptor || isEelPortAccessibleLocally(localPortUShort, localPortUShort, eelDescriptor)) {
      return Mapping(local = localPort, remote = localPort)
    }
    return null
  }

  private suspend fun Project.runTunnel(forRemoteServer: Boolean, disposable: Disposable): Mapping {
    try {
      val (proxy, mapping) = if (forRemoteServer) runServerTunnel() else runClientTunnel()
      val job = proxyCoroutineScope.launch {
        try {
          proxy.runForever()
        }
        finally {
          LOG.info("An IJent proxy $mapping was terminated")
        }
      }
      Disposer.register(disposable) {
        job.cancel()
      }
      return mapping
    }
    catch (e: Exception) {
      throw IllegalStateException("Unable to start a debugger proxy", e)
    }
  }

  @OptIn(EelDelicateApi::class)
  private suspend fun Project.runClientTunnel(): Pair<EelProxy, Mapping> {
    val localPort = NetUtils.findAvailableSocketPort()
    val proxy = eelProxy()
      .acceptOnTcpPort(getEelDescriptor().toEelApi().tunnels, port = 0.toUShort())
      .connectToTcpPort(localEel.tunnels, port = localPort.toUShort())
      .onConnection { LOG.info("Debugger proxy $localPort accepted an incoming connection") }
      .onConnectionClosed { LOG.info("Debugger proxy $localPort closed a connection") }
      .onConnectionError { LOG.error("A debugger proxy $localPort error occurred: ${it.message}") }
      .eelIt()
    val remotePort = proxy.acceptor.boundAddress.port.toInt()
    LOG.info("A local port ${localPort} can be accessed via 127.0.0.1:$remotePort on a remote machine")
    return proxy to Mapping(local = localPort, remote = remotePort)
  }

  @OptIn(EelDelicateApi::class)
  private suspend fun Project.runServerTunnel(): Pair<EelProxy, Mapping> {
    val localPort = NetUtils.findAvailableSocketPort()
    // there is no fast and easy way to get a free port on the remote side
    // 49152 - 65535 is the range suggested by IANA as a safe range for dynamic ports
    val remotePort = ThreadLocalRandom.current().nextInt(49152, 65535)
    val proxy = eelProxy()
      .acceptOnTcpPort(localEel.tunnels, port = localPort.toUShort())
      .connectToTcpPort(getEelDescriptor().toEelApi().tunnels, port = remotePort.toUShort())
      .onConnection { LOG.info("Debugger proxy [$localPort : $remotePort] accepted an incoming connection") }
      .onConnectionClosed { LOG.info("Debugger proxy [$localPort : $remotePort] closed a connection") }
      .onConnectionError { LOG.error("A debugger proxy [$localPort : $remotePort] error occurred: ${it.message}") }
      .eelIt()
    return proxy to Mapping(local = localPort, remote = remotePort)
  }
}

