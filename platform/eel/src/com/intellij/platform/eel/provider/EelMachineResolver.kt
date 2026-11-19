// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelMachineProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelMachineConnectionProvider
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
 * The usual one-call path from a descriptor to a working environment when the environment's lifecycle is already managed by the IDE
 * (for example, when the descriptor comes from an open `Project` or a routed `Path`). It starts or reuses the environment, which may
 * take time and may fail. For [LocalEelDescriptor] it returns the local environment with no connection overhead.
 *
 * If the descriptor was created outside an open project, or you need the environment and its [com.intellij.platform.eel.fileSystem]
 * to stay open while you work with them, use [withEelApi] instead.
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
 * Resolves the [EelMachine] behind this descriptor, holds a lease on its connection while [block] runs, and returns the result of [block].
 *
 * The lease starts before [block] is called and ends when [block] completes, whether normally, by an exception, or by cancellation.
 * Concurrent and nested calls for the same machine share one [EelApi] and one [com.intellij.platform.eel.fileSystem]. When the last
 * lease on the machine ends (including the leases of open IDE projects), the file system and the remote agent session are closed.
 * Do not let the [EelApi] or its file system escape [block]: they may be closed right after [block] returns.
 *
 * Why a block instead of making [EelApi] implement [AutoCloseable]: an [EelApi] is backed by a machine-wide shared session, and most
 * callers of the parameterless [toEelApi] are one-shot lookups that never close the returned instance. A block makes the lease
 * impossible to leak and keeps the closing of one caller from terminating a session still used by another lease or an open project.
 *
 * A component that needs the connection for its whole lifetime holds it with a coroutine:
 * ```kotlin
 * scope.launch {
 *   descriptor.withEelApi { api ->
 *     ready.complete(api)
 *     awaitCancellation()
 *   }
 * }
 * ```
 * Cancelling that coroutine releases the lease.
 *
 * @throws EelUnavailableException if the environment cannot be reached.
 * @see EelMachine.withEelApi
 */
@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun <T> EelDescriptor.withEelApi(block: suspend (EelApi) -> T): T {
  return resolveEelMachine().withEelApi(this, block)
}

/**
 * Holds a lease on the connection to this machine while [block] runs and returns the result of [block].
 *
 * [descriptor] defines the path namespace of the [EelApi] passed to [block], as in [EelMachine.toEelApi].
 * See [EelDescriptor.withEelApi] for the lease semantics.
 *
 * @throws EelUnavailableException if the environment cannot be reached.
 */
@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun <T> EelMachine.withEelApi(descriptor: EelDescriptor, block: suspend (EelApi) -> T): T {
  return EelMachineConnectionProvider.registry.withConnection(this, descriptor, block)
}

/**
 * SPI that maps an [EelDescriptor] to the [EelMachine] backing it.
 *
 * Each environment integration (WSL, Docker, SSH, …) contributes a resolver, discovered via [EelMachineResolver.Provider]. Application
 * code does not implement or call this directly — use [EelDescriptor.resolveEelMachine] or [EelDescriptor.getResolvedEelMachine]
 * instead.
 */
@ApiStatus.Experimental
interface EelMachineResolver {
  companion object {
    private val resolver = ServiceLoader.load(Provider::class.java, Provider::class.java.classLoader)
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

  /**
   * SPI for providing [EelMachineResolver] instances. Loaded via [ServiceLoader] from `eel-impl`.
   */
  @ApiStatus.Internal
  interface Provider {
    fun getAll(): List<EelMachineResolver>
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
 * Compares this with [they] descriptor using deepest possible method.
 * Bare `==` makes different between `wsl.localhost` and `wsl$` for example, and Docker might also have similar issues.
 * This method does its best and usually works.
 *
 * It uses [getResolvedEelMachine], so it never starts an environment.
 * It reads the machine of [they] only when the machine of this descriptor is unknown.
 * Two unknown machines give `false`.
 */
@ApiStatus.Internal
@Suppress("EelDescriptorEquality")
fun EelDescriptor.equalsMayBeUsingMachine(they: EelDescriptor): Boolean {
  if (they == this) {
    return true
  }
  val myMachine = getResolvedEelMachine()
  if (myMachine != null) {
    return myMachine.ownsDescriptor(they)
  }
  return they.getResolvedEelMachine()?.ownsDescriptor(this) == true
}