// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath.OS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.nio.file.Path

/**
 * A marker interface that indicates an environment where native file chooser dialogs should be disabled.
 *
 * When an [EelDescriptor] implements this interface, the IDE will use its own file chooser dialog
 * instead of the native operating system dialog when working with projects in this environment.
 *
 * This is particularly useful for remote environments like Docker containers where the native
 * file chooser would not have access to the remote filesystem.
 *
 * @see com.intellij.openapi.fileChooser.impl.LocalFileChooserFactory.canUseNativeDialog
 */
@ApiStatus.OverrideOnly
@ApiStatus.Internal
interface EelDescriptorWithoutNativeFileChooserSupport : EelDescriptor

/**
 * Identifies a specific machine — such as a Docker container, WSL distribution, or SSH host.
 *
 * Multiple [EelDescriptor]s may map to the same machine.
 * This interface is useful when caching, deduplicating, or sharing resources across descriptor instances.
 *
 * ## Examples
 * - For WSL: all descriptors with base paths like `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu` point to the same [EelMachine].
 * - For Docker: descriptors with `/docker-<id>/...` paths share the same container machine.
 *
 * Use this when caching or pooling long-lived data that’s stable across paths.
 */
@ApiStatus.Experimental
interface EelMachine {
  /**
   * The platform of an environment corresponding to this [EelMachine].
   */
  @get:ApiStatus.Experimental
  val osFamily: EelOsFamily

  /**
   * Describes machine in a user-readable manner, i.e: "Docker: <container_name>" or "Wsl: <distro name>".
   * Format is *not* specified but guaranteed to be user-readable.
   */
  @get:ApiStatus.Experimental
  val name: @NonNls String

  /**
   * Converts this machine into a [EelApi] — starts or reuses a running environment.
   */
  @ApiStatus.Experimental
  suspend fun toEelApi(descriptor: EelDescriptor): EelApi
}

/**
 * Specialization of [EelDescriptor] that resolves to a path-based environment.
 *
 * These descriptors are tied to a concrete filesystem root (e.g. `\\wsl$\Ubuntu` or `/docker-xyz`).
 * Different paths to the same logical environment yield different descriptors — even if they point to the same [EelMachine].
 *
 * This allows tools to distinguish between environments even if the underlying host is the same.
 */
@ApiStatus.Experimental
interface EelPathBoundDescriptor : EelDescriptor {
  /**
   * A platform-specific base path representing the environment's root.
   *
   * Examples:
   * - `\\wsl$\Ubuntu` for a WSL distribution
   * - `/docker-12345/` for Docker containers
   */
  val rootPath: Path
}

/**
 * Represents an abstract description of an environment where [EelApi] may exist.
 *
 * ## Concepts
 * - [EelDescriptor] describes a *specific path-based access* to an environment.
 * - [EelMachine] describes the *physical or logical host* (e.g., WSL distribution, Docker container).
 *
 * For example, two descriptors like `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu` may point to the same [EelMachine],
 * but they should be treated as distinct [EelDescriptor]s since tooling behavior or caching may differ per path.
 *
 * ## Use cases
 * - If you're caching data that is *machine-wide*, prefer using [machine] as a cache key instead of [EelDescriptor].
 * - If you're accessing a specific path (e.g., resolving symbolic links or permissions), use [EelDescriptor].
 *
 * ## Examples
 * - [LocalEelDescriptor] refers to the machine where the IDE runs (same machine and descriptor).
 * - WSL: Each distribution is a machine. Paths like `\\wsl$\Ubuntu` and `\\wsl.localhost\Ubuntu` are different descriptors pointing to the same machine.
 * - Docker: Each container is a machine. Paths like `/docker-abc123/...` are descriptors.
 * - SSH: Each remote host is a machine. A descriptor may correspond to a specific session or path.
 *
 * ## Lifecycle
 * [EelDescriptor] is:
 * - **Lightweight**: Unlike [EelApi], it does not represent a running environment.
 * - **Durable**: It can persist even when [EelApi] becomes unavailable (e.g., Docker stopped).
 *
 * ## Access
 * Use `getEelDescriptor()` to resolve a descriptor from a [Path] or [Project].
 *
 * ```kotlin
 * val descriptor = Path.of("\\\\wsl.localhost\\Ubuntu\\home\\me").getEelDescriptor()
 * val machine = descriptor.machine  // Shared between paths pointing to the same distro/container
 * val api = descriptor.toEelApi()   // Starts or connects to the actual environment
 * ```
 */
@ApiStatus.Experimental
interface EelDescriptor {
  @Deprecated("Use platform instead", ReplaceWith("platform"))
  @get:ApiStatus.Internal
  val operatingSystem: OS
    get() = when (osFamily) {
      EelOsFamily.Windows -> OS.WINDOWS
      EelOsFamily.Posix -> OS.UNIX
    }

  /**
   * Returns the machine this descriptor belongs to.
   *
   * Multiple descriptors may resolve to the same [EelMachine], e.g.:
   * - Docker paths with different mount points
   * - WSL descriptors using `wsl$` vs `wsl.localhost`
   */
  val machine: EelMachine

  /**
   * The platform of an environment corresponding to this [EelDescriptor].
   */
  @get:ApiStatus.Experimental
  val osFamily: EelOsFamily get() = machine.osFamily

  @ApiStatus.Experimental
  suspend fun toEelApi(): EelApi {
    return machine.toEelApi(this)
  }

  /**
   * Retrieves an instance of [EelApi] corresponding to this [EelDescriptor].
   * This method may run a container, so it could suspend for a long time.
   */
  @Deprecated("Use toEelApi() instead", replaceWith = ReplaceWith("toEelApi()"))
  @ApiStatus.Internal
  suspend fun upgrade(): EelApi = toEelApi()
}
