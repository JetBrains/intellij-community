// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.base

import com.intellij.platform.eel.EelDescriptor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * Housekeeping for eel environment
 */
interface EelEnvironmentHousekeepingService {
  suspend fun prepareEnvironment(descriptor: EelDescriptor, coroutineScope: CoroutineScope): Map<String, String>

  companion object {
    private val provider: EelEnvironmentHousekeepingServiceProvider? by lazy {
      ServiceLoader.load(EelEnvironmentHousekeepingServiceProvider::class.java, EelEnvironmentHousekeepingServiceProvider::class.java.classLoader).firstOrNull()
    }

    suspend fun prepareEnvironment(descriptor: EelDescriptor, coroutineScope: CoroutineScope): Map<String, String> {
      provider?.let {
        return it.getAll().flatMap { it.prepareEnvironment(descriptor, coroutineScope).entries }.groupBy { it.key }
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
fun interface EelEnvironmentHousekeepingServiceProvider {
  fun getAll(): List<EelEnvironmentHousekeepingService>
}
