// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.execution.eel.MultiRoutingFileSystemUtils
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.EelEnvironmentInitializer
import com.intellij.platform.eel.provider.resolveEelMachine
import kotlinx.coroutines.CancellationException

private val LOG = logger<TcpEelEnvironmentInitializer>()

class TcpEelEnvironmentInitializer : EelEnvironmentInitializer {
  override suspend fun tryInitialize(eelDescriptor: EelDescriptor): EelMachine? {
    if (!MultiRoutingFileSystemUtils.isMultiRoutingFsEnabled) {
      return null
    }
    val descriptor = eelDescriptor as? TcpEelDescriptor ?: return null
    val tcpMachine = descriptor.resolveEelMachine() as? TcpEelMachine ?: return null
    try {
      tcpMachine.toEelApi(descriptor) // deploy ijent
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      // A failed IJent deploy must not make the environment unavailable: return the machine anyway and let
      // it be deployed lazily on the next request. Otherwise project opening would fall back to local Eel.
      LOG.error("Failed to deploy IJent for $descriptor", e)
    }
    return tcpMachine
  }
}
