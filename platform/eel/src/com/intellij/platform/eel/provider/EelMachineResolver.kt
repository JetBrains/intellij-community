// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("EelMachineProviderUtil")

package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelUnavailableException
import kotlinx.coroutines.CoroutineScope
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
 * Creates [EelDescriptor] instances directly from target-specific parameters (such as a WSL distribution ID, Docker container ID,
 * or SSH host).
 *
 * **Note:** This is an unusual way to obtain an [EelDescriptor], intended only for tools that need to connect to arbitrary environments
 * not associated with an open IDE project or a routed path. In most cases, it is better to use `Path.getEelDescriptor` or
 * `Project.getEelDescriptor` instead.
 */
@ApiStatus.Experimental
object EelDescriptors {
  private val provider = ServiceLoader
    .load(EelMachineResolver.Provider::class.java, EelMachineResolver.Provider::class.java.classLoader)
    .firstOrNull()

  /**
   * Just an alias for [LocalEelDescriptor], so all descriptors are in a single place.
   */
  @ApiStatus.Experimental
  val local: LocalEelDescriptor = LocalEelDescriptor

  /**
   * Creates an [EelDescriptor] for the WSL distribution with the given [distributionId],
   * or returns `null` if the WSL Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  fun wsl(distributionId: String): EelDescriptor? {
    return provider?.wsl().orEmpty().firstNotNullOfOrNull { it.resolveWslEelDescriptor(distributionId) }
  }

  /**
   * Creates an [EelDescriptor] for the Docker container with the given [containerId] on the default server,
   * or returns `null` if the Docker Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  fun docker(containerId: String): EelDescriptor? {
    return docker(containerId, null)
  }

  /**
   * Creates an [EelDescriptor] for the Docker container with the given [containerId] and optional server [endpoint],
   * or returns `null` if the Docker Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  fun docker(containerId: String, endpoint: String?): EelDescriptor? {
    return provider?.docker().orEmpty().firstNotNullOfOrNull { it.resolveDockerEelDescriptor(containerId, endpoint) }
  }

  // TODO Move UserHostPort here.

  /**
   * Creates an [EelDescriptor] for the SSH host with the given [userHostPort] (assuming [EelOsFamily.Posix]),
   * or returns `null` if the SSH Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  fun ssh(userHostPort: String): EelDescriptor? {
    return ssh(userHostPort, EelOsFamily.Posix)
  }

  /**
   * Creates an [EelDescriptor] for the SSH host with the given [userHostPort] and [osFamily],
   * or returns `null` if the SSH Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  fun ssh(userHostPort: String, osFamily: EelOsFamily): EelDescriptor? {
    return provider?.ssh().orEmpty().firstNotNullOfOrNull { it.resolveSshEelDescriptor(userHostPort, osFamily) }
  }
}

/**
 * Resolves [EelMachine] instances directly from target parameters such as a WSL distribution ID, a Docker container ID,
 * or an SSH host.
 *
 * **Note:** This API is only for tools that connect to an environment without an open IDE project or a routed path.
 * In most cases, resolve the machine from a descriptor via [EelDescriptor.resolveEelMachine] instead.
 */
@ApiStatus.Experimental
object EelMachines {
  private val provider = ServiceLoader
    .load(EelMachineResolver.Provider::class.java, EelMachineResolver.Provider::class.java.classLoader)
    .firstOrNull()

  /**
   * Alias for [LocalEelMachine] so that all machines are in one place.
   */
  @ApiStatus.Experimental
  val local: LocalEelMachine = LocalEelMachine

  /**
   * Resolves the [EelMachine] for the WSL distribution with the given [distributionId].
   * Returns `null` if the WSL Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  suspend fun wsl(distributionId: String): EelMachine? {
    return provider?.wslMachines().orEmpty().firstNotNullOfOrNull { it.resolveWslEelMachine(distributionId) }
  }

  /**
   * Resolves the [EelMachine] for the Docker container with the given [containerId] on the default server.
   * Returns `null` if the Docker Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  suspend fun docker(containerId: String): EelMachine? {
    return docker(containerId, null)
  }

  /**
   * Resolves the [EelMachine] for the Docker container with the given [containerId] and optional server [endpoint].
   * Returns `null` if the Docker Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  suspend fun docker(containerId: String, endpoint: String?): EelMachine? {
    return provider?.dockerMachines().orEmpty().firstNotNullOfOrNull { it.resolveDockerEelMachine(containerId, endpoint) }
  }

  // TODO Move UserHostPort here.

  /**
   * Resolves the [EelMachine] for the SSH host with the given [userHostPort].
   * Returns `null` if the SSH Eel backend is not installed or enabled.
   */
  @ApiStatus.Experimental
  suspend fun ssh(userHostPort: String): EelMachine? {
    return provider?.sshMachines().orEmpty().firstNotNullOfOrNull { it.resolveSshEelMachine(userHostPort) }
  }
}

/**
 * Resolves the [EelMachine] behind this descriptor and connects to it, returning a live [EelApi].
 *
 * The usual one-call path from a descriptor to a working environment when the environment's lifecycle is already managed by the IDE
 * (for example, when the descriptor comes from an open `Project` or a routed `Path`). It starts or reuses the environment, which may
 * take time and may fail. For [LocalEelDescriptor] it returns the local environment with no connection overhead.
 *
 * If the descriptor was created outside an open project (such as via [EelDescriptors]) or you need a reference-counted lease that
 * closes the environment and its [com.intellij.platform.eel.fileSystem] when your usage scope ends, use `toEelApi(lifetime)` instead.
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
 * Resolves the [EelMachine] behind this descriptor and acquires a reference-counted connection lease bound to [lifetime],
 * returning a live [EelApi].
 *
 * Unlike the parameterless [toEelApi], this overload ties the environment's lifecycle to [lifetime]. Use it when connecting to an
 * environment outside an open IDE project (for example via [EelDescriptors]) or when temporarily accessing
 * [com.intellij.platform.eel.fileSystem]. The lease remains active until [lifetime] is cancelled or completes. When all leases on the
 * underlying [EelMachine] (including any open IDE projects) end, its machine-bound [com.intellij.platform.eel.fileSystem] and
 * underlying remote agent session are closed.
 *
 * Why a [lifetime] parameter instead of making [EelApi] implement [AutoCloseable]: an [EelApi] is backed by a machine-wide shared
 * session, and most callers of the parameterless [toEelApi] are one-shot lookups that never close the returned instance. Taking
 * [lifetime] at acquisition time distinguishes lease-holding callers from fire-and-forget callers, so reference counts cannot leak
 * and closing one caller's scope never terminates a session still used by another lease or an open project.
 *
 * @throws EelUnavailableException if the environment cannot be reached.
 * @see EelMachine.toEelApi
 */
@ApiStatus.Experimental
@Throws(EelUnavailableException::class)
suspend fun EelDescriptor.toEelApi(lifetime: CoroutineScope): EelApi {
  return resolveEelMachine().toEelApi(this, lifetime)
}

/**
 * SPI that maps an [EelDescriptor] to the [EelMachine] backing it.
 *
 * Each environment integration (WSL, Docker, SSH, …) contributes a resolver, discovered via [EelMachineResolver.Provider]. Application
 * code does not implement or call this directly — use [EelDescriptor.resolveEelMachine], [EelDescriptor.getResolvedEelMachine],
 * [EelDescriptors], or [EelMachines] instead.
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
   * SPI for providing [EelMachineResolver] and target descriptor provider instances. Loaded via [ServiceLoader] from `eel-impl`.
   */
  @ApiStatus.Internal
  interface Provider {
    fun getAll(): List<EelMachineResolver>

    fun wsl(): List<WslEelDescriptorProvider> = emptyList()

    interface WslEelDescriptorProvider {
      fun resolveWslEelDescriptor(distributionId: String): EelDescriptor?
    }

    fun wslMachines(): List<WslEelMachineProvider> = emptyList()

    interface WslEelMachineProvider {
      suspend fun resolveWslEelMachine(distributionId: String): EelMachine?
    }

    fun docker(): List<DockerEelDescriptorProvider> = emptyList()

    interface DockerEelDescriptorProvider {
      fun resolveDockerEelDescriptor(containerId: String, endpoint: String?): EelDescriptor?
    }

    fun dockerMachines(): List<DockerEelMachineProvider> = emptyList()

    interface DockerEelMachineProvider {
      suspend fun resolveDockerEelMachine(containerId: String, endpoint: String?): EelMachine?
    }

    fun ssh(): List<SshEelDescriptorProvider> = emptyList()

    interface SshEelDescriptorProvider {
      fun resolveSshEelDescriptor(userHostPort: String, osFamily: EelOsFamily): EelDescriptor?
    }

    fun sshMachines(): List<SshEelMachineProvider> = emptyList()

    interface SshEelMachineProvider {
      suspend fun resolveSshEelMachine(userHostPort: String): EelMachine?
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