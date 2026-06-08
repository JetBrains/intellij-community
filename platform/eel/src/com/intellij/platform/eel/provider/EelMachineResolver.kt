// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelMachineProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelUnavailableException
import org.jetbrains.annotations.ApiStatus
import java.util.ServiceLoader

/**
 * Resolves the [EelMachine] that backs this [EelDescriptor], consulting the relevant environment integration when needed.
 *
 * Several descriptors may share one machine (see [EelMachine]), so use the machine — not the descriptor — as the key when caching or
 * sharing machine-wide resources. This is the resolving counterpart of [getResolvedEelMachine]: it actively performs resolution, so it
 * suspends and may fail, whereas [getResolvedEelMachine] only returns an already-known machine.
 *
 * @throws EelUnavailableException if the environment can no longer be reached (e.g. the container was removed or the host is gone).
 * @throws IllegalStateException if no registered resolver recognizes this descriptor.
 */
@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun EelDescriptor.resolveEelMachine(): EelMachine {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineResolver.getAll().firstNotNullOfOrNull { it.resolveEelMachine(this) }
         ?: throw IllegalStateException("No EelMachine found for descriptor: $this (${this.name})")
}

/**
 * Returns the [EelMachine] behind this [EelDescriptor] if it has already been resolved, or `null` otherwise.
 *
 * Non-suspending, best-effort counterpart of [resolveEelMachine]: it never triggers resolution or performs I/O, so it is safe to call
 * from any context. A `null` result means "not resolved yet", not "no such machine".
 */
@ApiStatus.Experimental
fun EelDescriptor.getResolvedEelMachine(): EelMachine? {
  if (this === LocalEelDescriptor) return LocalEelMachine
  return EelMachineResolver.getAll().firstNotNullOfOrNull {
    it.getResolvedEelMachine(this)
  }
}

/**
 * Resolves the [EelMachine] behind this descriptor and connects to it, returning a live [EelApi].
 *
 * The usual one-call path from a descriptor to a working environment: it starts or reuses the environment, which may take time and may
 * fail. For [LocalEelDescriptor] it returns the local environment with no connection overhead.
 *
 * @throws EelUnavailableException if the environment cannot be reached.
 * @see EelMachine.toEelApi
 */
@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun EelDescriptor.toEelApi(): EelApi {
  return resolveEelMachine().toEelApi(this)
}

/**
 * SPI that maps an [EelDescriptor] to the [EelMachine] backing it.
 *
 * Each environment integration (WSL, Docker, SSH, …) contributes a resolver, discovered via [EelMachineResolverProvider]. Application
 * code does not implement or call this directly — use the `EelDescriptor.resolveEelMachine` / `EelDescriptor.getResolvedEelMachine`
 * extensions instead.
 */
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

  /**
   * Best-effort, non-suspending lookup behind the `EelDescriptor.getResolvedEelMachine` extension. Returns the already-resolved machine
   * for [eelDescriptor], or `null` if this resolver does not handle it or has not resolved it yet.
   */
  @ApiStatus.Internal
  fun getResolvedEelMachine(eelDescriptor: EelDescriptor): EelMachine?

  /**
   * Resolves the machine for [eelDescriptor], or returns `null` if this resolver does not handle that descriptor.
   *
   * @throws EelUnavailableException if the descriptor is handled but the environment cannot be reached.
   */
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
