// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelMachineProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelUnavailableException
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun EelDescriptor.resolveEelMachine(): EelMachine {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineResolver.getAll().firstNotNullOfOrNull { it.resolveEelMachine(this) }
         ?: throw IllegalStateException("No EelMachine found for descriptor: $this (${this.name})")
}

@ApiStatus.Experimental
fun EelDescriptor.getResolvedEelMachine(): EelMachine? {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineResolver.getAll().firstNotNullOfOrNull {
    it.getResolvedEelMachine(this)
  }
}

/**
 * @see [com.intellij.platform.eel.EelMachine.toEelApi]
 */
@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun EelDescriptor.toEelApi(): EelApi {
  return resolveEelMachine().toEelApi(this)
}

@ApiStatus.Experimental
interface EelMachineResolver {
  companion object {
    private val resolver = ServiceLoader.load(EelMachineResolverProvider::class.java, EelMachineResolverProvider::class.java.classLoader)
      .firstOrNull()

    fun getAll(): List<EelMachineResolver> {
      return resolver?.getAll() ?: emptyList()
    }

    @ApiStatus.Internal
    suspend fun getEelMachineByInternalName(internalName: String): EelMachine {
      if (internalName == LocalEelMachine.internalName) {
        return LocalEelMachine
      }
      return getAll().firstNotNullOfOrNull {
        it.resolveEelMachineByInternalName(internalName)
      } ?: throw IllegalStateException("No EelMachine found for internal name: $internalName")
    }
  }

  @ApiStatus.Internal
  fun getCachedDescriptors(): Collection<EelDescriptor> {
    // TODO implement everywhere
    return emptyList()
  }

  @ApiStatus.Internal
  fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  @Throws(EelUnavailableException::class)
  suspend fun resolveEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  @ApiStatus.Internal
  suspend fun resolveEelMachineByInternalName(internalName: String): EelMachine?
}

/**
 * SPI for providing [EelMachineResolver] instances. Loaded via [ServiceLoader] from `eel-impl`.
 */
@ApiStatus.Internal
fun interface EelMachineResolverProvider {
  fun getAll(): List<EelMachineResolver>
}
