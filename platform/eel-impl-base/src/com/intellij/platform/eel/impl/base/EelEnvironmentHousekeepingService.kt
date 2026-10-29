// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.base

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * Housekeeping for eel environment
 */
@VisibleToClasses(
    "com.intellij.platform.eel.impl.provider.EelEnvironmentHousekeepingServiceProviderImpl",
    "com.intellij.platform.eel.impl.provider.IjentEnvironmentServiceProviderImplBridge",
    "com.intellij.platform.ijent.impl.base.GrpcIjentExecApiDelegate",
    "com.intellij.platform.ijent.ssh.impl.IjentSshAgentForwardingService",
)
interface EelEnvironmentHousekeepingService {
  /**
   * [tunnels] belong to the Eel that is being prepared.
   * Use them instead of `descriptor.toEelApi()`.
   * That call can resolve or deploy a different Eel for the same descriptor, and it can wait for the Eel that is being prepared.
   */
  suspend fun prepareEnvironment(descriptor: EelDescriptor, tunnels: EelTunnelsApi, coroutineScope: CoroutineScope): Map<String, String>

  companion object {
    private val provider: EelEnvironmentHousekeepingServiceProvider? by lazy {
      ServiceLoader.load(EelEnvironmentHousekeepingServiceProvider::class.java, EelEnvironmentHousekeepingServiceProvider::class.java.classLoader).firstOrNull()
    }

    suspend fun prepareEnvironment(descriptor: EelDescriptor, tunnels: EelTunnelsApi, coroutineScope: CoroutineScope): Map<String, String> {
      provider?.let {
        return it.getAll().flatMap { it.prepareEnvironment(descriptor, tunnels, coroutineScope).entries }.groupBy { it.key }
          .mapValues { it.value.last().value }
      }
      return emptyMap()
    }
  }
}

/**
 * SPI for providing [EelEnvironmentHousekeepingService] instances. Loaded via [ServiceLoader] from `ijent-ssh`.
 */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.eel.impl.provider.EelEnvironmentHousekeepingServiceProviderImpl",
)
fun interface EelEnvironmentHousekeepingServiceProvider {
  fun getAll(): List<EelEnvironmentHousekeepingService>
}
