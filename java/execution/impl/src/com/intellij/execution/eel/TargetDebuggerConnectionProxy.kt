// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
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

internal object TargetDebuggerConnectionProxy {

  @Service(Service.Level.PROJECT)
  private class ProxyCoroutineScopeHolder(val coroutineScope: CoroutineScope)

  private val Project.proxyCoroutineScope: CoroutineScope
    get() = service<ProxyCoroutineScopeHolder>().coroutineScope

  fun getProxy(project: Project, disposable: Disposable): Pair<Int, Int> = runBlockingMaybeCancellable {
    project.getDirectPort() ?: project.runTunnel(disposable)
  }

  // a special case for WSL with a mirrored network mode
  private suspend fun Project.getDirectPort(): Pair<Int, Int>? {
    val localPort = NetUtils.findAvailableSocketPort()
    val localPortUShort = localPort.toUShort()
    val eelDescriptor = getEelDescriptor()
    if (eelDescriptor == LocalEelDescriptor || isEelPortAccessibleLocally(localPortUShort, localPortUShort, eelDescriptor)) {
      return localPort to localPort
    }
    return null
  }

  @OptIn(EelDelicateApi::class)
  private suspend fun Project.runTunnel(disposable: Disposable): Pair<Int, Int> {
    val remoteTunnels = getEelDescriptor()
      .toEelApi()
      .tunnels

    val localPort = NetUtils.findAvailableSocketPort()
    val remotePort = getEphemeralPort()
    try {
      val proxy = eelProxy()
        .acceptOnTcpPort(localEel.tunnels, port = localPort.toUShort())
        .connectToTcpPort(remoteTunnels, port = remotePort.toUShort())
        .onConnection { LOG.info("Debugger proxy [$localPort : $remotePort] accepted an incoming connection") }
        .onConnectionClosed { LOG.info("Debugger proxy [$localPort : $remotePort] closed a connection") }
        .onConnectionError { LOG.error("A debugger proxy [$localPort : $remotePort] error occurred: ${it.message}") }
        .eelIt()
      val job = proxyCoroutineScope.launch {
        try {
          proxy.runForever()
        }
        finally {
          LOG.info("An IJent proxy from $localPort to $remotePort was terminated")
        }
      }
      Disposer.register(disposable) {
        job.cancel()
      }
      return localPort to remotePort
    }
    catch (e: Exception) {
      LOG.error("Unable to start a proxy from $localPort to $remotePort", e)
      throw IllegalStateException("Unable to start a proxy from $localPort to $remotePort", e)
    }
  }

  // there is no fast and easy way to get a free port on the remote side
  // 49152 - 65535 is the range suggested by IANA as a safe range for dynamic ports
  private fun getEphemeralPort(): Int = ThreadLocalRandom.current().nextInt(49152, 65535)
}

