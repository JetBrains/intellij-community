// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.execution.eel.MultiRoutingFileSystemUtils
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.resolveEelMachine
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString

class TcpEelProvider : EelProvider {
  override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine? {
    if (!MultiRoutingFileSystemUtils.isMultiRoutingFsEnabled) {
      return null
    }
    val descriptor = getEelDescriptor(Path(path)) ?: return null
    val tcpMachine = descriptor.resolveEelMachine() as? TcpEelMachine ?: return null
    tcpMachine.toEelApi(descriptor) // deploy ijent
    return tcpMachine
  }

  override fun getEelDescriptor(path: @MultiRoutingFileSystemPath Path): EelDescriptor? {
    val sanitizedPath = path.invariantSeparatorsPathString
    val (internalName, osFamily) = TcpEelPathParser.extractInternalMachineId(sanitizedPath) ?: return null
    return TcpEelPathParser.toDescriptor(internalName, osFamily)
  }

  override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? {
    return if (eelDescriptor is TcpEelDescriptor) listOf(eelDescriptor.rootPathString) else null
  }
}