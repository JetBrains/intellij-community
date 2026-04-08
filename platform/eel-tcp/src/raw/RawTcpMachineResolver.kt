// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.EelMachineResolver
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

internal class RawTcpMachineResolver(private val coroutineScope: CoroutineScope) : EelMachineResolver {
  private val cache = ConcurrentHashMap<String, RawTcpEelMachine>()

  override fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine? {
    val descriptor = eelDescriptor as? RawTcpEelDescriptor ?: return null
    val deploy = descriptor.tcpDeploy
    return cache.computeIfAbsent(RawTcpConsts.internalName(deploy)) {
      createMachine(deploy)
    }
  }

  override suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine? {
    return getResolvedEelMachine(eelDescriptor)
  }

  override suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine? {
    val deploy = RawTcpConsts.extractTcpDeploy(internalName) ?: return null
    return cache.computeIfAbsent(internalName) {
      createMachine(deploy)
    }
  }

  private fun createMachine(deploy: TcpDeployInfo.FixedPort): RawTcpEelMachine {
    return RawTcpEelMachine(
      deploy = deploy,
      coroutineScope = coroutineScope.childScope("Scope for ${RawTcpEelMachine::class.simpleName} $deploy")
    )
  }
}
