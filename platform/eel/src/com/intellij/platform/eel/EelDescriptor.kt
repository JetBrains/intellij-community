// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath.OS
import com.intellij.platform.util.annotations.VisibleToClasses
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

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
 * Marker interface enforcing an isolated workspace for the project located in a container. The workspace file located under config/workspace
 * is shared by default if the project is both opened locally and mounted, which may cause undesirable side effects.
 */
@ApiStatus.OverrideOnly
@ApiStatus.Internal
interface EelDescriptorWithIsolatedWorkspace : EelDescriptor

/**
 * Marker interface that indicates ssh agent forwarding by default
 */
@ApiStatus.OverrideOnly
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.docker.ijent.DockerEelDescriptor",
  "com.intellij.platform.ijent.ssh.impl.IjentSshAgentForwardingService",
)
interface EelDescriptorWithSshForwardingEnabled : EelDescriptor

/**
 * The deployment of this environment can ask for user interaction, for example an SSH authentication dialog.
 * The synchronous NIO bridge fails fast instead of awaiting such a deployment, because the dialog
 * needs the thread the bridge blocks (IJPL-245001).
 *
 * Read [deploymentMayRequireUserInteraction]; the instanceof check alone is not the answer:
 * a delegating descriptor reports the answer of its current target, and the answer changes with the target.
 */
@ApiStatus.OverrideOnly
@ApiStatus.Internal
interface EelDescriptorWithInteractiveDeployment : EelDescriptor {
  val deploymentMayRequireUserInteraction: Boolean
    get() = true
}

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

  @get:ApiStatus.Internal
  val internalName: String

  /**
   * Converts this machine into a [EelApi] — starts or reuses a running environment.
   * @throws EelUnavailableException if eel is unavailable (i.e. remote machine is gone, docker container removed e.t.c.). Show it to a user, ask to fix and try again.
   */
  @ApiStatus.Experimental
  @Throws(EelUnavailableException::class)
  suspend fun toEelApi(descriptor: EelDescriptor): EelApi

  fun ownsDescriptor(descriptor: EelDescriptor): Boolean
}

/**
 * An [EelMachine] that reports whether it holds a live connection.
 *
 * The value is a snapshot and can change at any moment. Treat `true` as a hint, for example
 * "an await of [EelMachine.toEelApi] only fetches the existing session", never as a correctness
 * guarantee. A machine that does not implement this interface does not report its state;
 * the absence of the interface is not evidence of a missing connection.
 */
@ApiStatus.Experimental
interface EelMachineWithConnectionState : EelMachine {
  val isConnected: Boolean
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
   * Describes descriptor in a user-readable manner, i.e: "Docker: <container_name>" or "Wsl: <distro name>".
   * Format is *not* specified but guaranteed to be user-readable.
   */
  @get:ApiStatus.Experimental
  val name: @NonNls String

  /**
   * The platform of an environment corresponding to this [EelDescriptor].
   */
  @get:ApiStatus.Experimental
  val osFamily: EelOsFamily
}
