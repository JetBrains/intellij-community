// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.annotations.MultiRoutingFileSystemPath
import com.intellij.platform.eel.provider.EelProvider
import com.intellij.platform.eel.provider.getResolvedEelMachine
import java.nio.file.Path
import kotlin.io.path.pathString

class TcpEelProvider : EelProvider {
  override suspend fun tryInitialize(path: @MultiRoutingFileSystemPath String): EelMachine? {
    val endpoint = path.extractTcpEndpoint() ?: return null
    return TcpEelRegistry.getInstance().register(endpoint).getResolvedEelMachine()
  }

  override fun getEelDescriptor(path: @MultiRoutingFileSystemPath Path): EelDescriptor? {
    val endpoint = path.pathString.extractTcpEndpoint() ?: return null
    return TcpEelRegistry.getInstance().get(endpoint)
  }

  override fun getCustomRoots(eelDescriptor: EelDescriptor): Collection<@MultiRoutingFileSystemPath String>? {
    return if (eelDescriptor is TcpEelDescriptor) listOf(eelDescriptor.rootPathString) else null
  }
}