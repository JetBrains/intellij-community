// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.openapi.components.service
import com.intellij.platform.eel.*
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.spi.IjentConnectionStrategy
import com.intellij.platform.ijent.spi.IjentSessionMediator
import com.intellij.platform.ijent.spi.IjentTcpSessionMediator
import com.intellij.platform.ijent.spi.IjentThreadPool
import com.intellij.platform.ijent.tcp.IjentIsolatedTcpDeployingStrategy
import com.intellij.platform.ijent.tcp.TcpEndpoint
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.SuspendingLazy
import com.intellij.util.suspendingLazy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import java.util.concurrent.ConcurrentHashMap

private val cache = ConcurrentHashMap<TcpEndpoint, SuspendingLazy<IjentSession<IjentPosixApi>>>()

private suspend fun getOrCreateIjentApi(connectionInfo: TcpEndpoint, descriptor: EelDescriptor): IjentPosixApi {
  return cache.compute(connectionInfo) { _, cachedValue ->
    val validCachedValue = when (cachedValue?.isInitialized()) {
      true -> {
        val ijentApi = runCatching { cachedValue.getInitialized() }.getOrNull()
        if (ijentApi?.isRunning == true) cachedValue else null
      }
      false -> cachedValue
      null -> null
    }
    validCachedValue ?: service<TcpEelScopeHolder>().coroutineScope.suspendingLazy {
      // FIXME[khb]: no proper deploy still. Requires manual connection
      val strategy = object : IjentIsolatedTcpDeployingStrategy() {
        val processExit = CompletableDeferred<Unit>()
        override suspend fun deployEnvironment(): IjentSessionMediator {
          val scope = service<TcpEelScopeHolder>().coroutineScope.childScope("Ijent TCP $connectionInfo", context = IjentThreadPool.coroutineContext)
          scope.coroutineContext.job.invokeOnCompletion { processExit.complete(Unit) }
          return IjentTcpSessionMediator(scope, processExit)
        }
        override fun closeConnection() {
          processExit.complete(Unit)
        }
        override suspend fun getTargetPlatform(): EelPlatform = EelPlatform.Linux(EelPlatform.Arch.X86_64)
        override suspend fun getConnectionStrategy(): IjentConnectionStrategy.Tcp {
          return IjentConnectionStrategy.Tcp(connectionInfo)
        }
      }
      strategy.createIjentSession()
    }
  }!!.getValue().getIjentInstance(descriptor)
}

class TcpEelMachine(internal val tcpEndpoint: TcpEndpoint) : EelMachine {
  override val osFamily: EelOsFamily = EelOsFamily.Posix
  override val name: String = "TCP ${tcpEndpoint.host}:${tcpEndpoint.port}"
  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    return getOrCreateIjentApi(tcpEndpoint, descriptor)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TcpEelMachine

    return tcpEndpoint == other.tcpEndpoint
  }

  override fun hashCode(): Int {
    return tcpEndpoint.hashCode()
  }

}
