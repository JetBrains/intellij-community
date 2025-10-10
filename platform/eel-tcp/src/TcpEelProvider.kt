// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.EelProvider
import java.nio.file.Path
import kotlin.io.path.pathString

class TcpEelProvider : EelProvider {
  override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String) {
    val endpoint = path.extractTcpEndpoint() ?: return
    TcpEelRegistry.getInstance().register(endpoint)
  }

  override fun getEelDescriptor(path: @MultiRoutingFileSystemPath Path): EelDescriptor? {
    val endpoint = path.pathString.extractTcpEndpoint() ?: return null
    return TcpEelRegistry.getInstance().get(endpoint)
  }

  override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? {
    return if (eelDescriptor is TcpEelDescriptor) listOf(eelDescriptor.rootPathString) else null
  }

  override fun getInternalName(eelMachine: EelMachine): String? {
    if (eelMachine !is TcpEelMachine) return null
    return "tcp-${eelMachine.tcpEndpoint.toPath()}"
  }

  override fun getEelMachineByInternalName(internalName: String): EelMachine? {
    if (!internalName.startsWith("tcp-")) return null
    val tcpDescriptor = internalName.extractTcpEndpoint() ?: return null
    return TcpEelMachine(tcpDescriptor)
  }
}