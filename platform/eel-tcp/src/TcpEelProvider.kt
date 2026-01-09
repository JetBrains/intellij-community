// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.resolveEelMachine
import java.nio.file.Path

class TcpEelProvider : EelProvider {
  override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine? {
    val internalName = TcpEelPathParser.extractInternalMachineId(path) ?: return null
    val descriptor = TcpEelRegistry.getInstance().register(internalName) ?: return null
    val tcpMachine = descriptor.resolveEelMachine() as? TcpEelMachine ?: return null
    tcpMachine.deploy()
    tcpMachine.waitForDeployment()
    return tcpMachine
  }

  override fun getEelDescriptor(path: @MultiRoutingFileSystemPath Path): EelDescriptor? {
    val internalName = TcpEelPathParser.extractInternalMachineId(path) ?: return null
    return TcpEelRegistry.getInstance().get(internalName)
  }

  override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? {
    return if (eelDescriptor is TcpEelDescriptor) listOf(eelDescriptor.rootPathString) else null
  }
}