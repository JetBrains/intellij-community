// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath.OS
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
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
@VisibleToClasses(
  "com.intellij.docker.ijent.DockerEelDescriptor",
  "com.intellij.openapi.fileChooser.impl.LocalFileChooserFactory",
  "com.intellij.platform.eel.tcp.TcpEelDescriptor",
  "com.intellij.platform.ide.impl.wsl.WslEelDescriptor",
  "com.intellij.platform.ijent.ssh.SshEelDescriptor",
)
interface EelDescriptorWithoutNativeFileChooserSupport : EelDescriptor

/**
 * Marker interface enforcing an isolated workspace for the project located in a container. The workspace file located under config/workspace
 * is shared by default if the project is both opened locally and mounted, which may cause undesirable side effects.
 */
@ApiStatus.OverrideOnly
@ApiStatus.Internal
interface EelDescriptorWithIsolatedWorkspace : EelDescriptor

/**
 * Whether the local ssh agent may answer a request from the environment without asking the user,
 * see [EelDescriptorWithSshForwardingEnabled.sshForwardingConsent].
 */
@ApiStatus.Internal
enum class SshForwardingConsentPolicy {
  /** Forward silently. Fits a local and short-lived environment, a Docker container for instance. */
  SILENT,

  /** Ask the user before the local agent answers a request from the environment. */
  ASK,
}

/**
 * Marker interface that indicates ssh agent forwarding by default.
 *
 * The socket is always exported to the environment; [sshForwardingConsent] decides whether the local agent answers the
 * requests coming from it silently or only after the user allows them.
 */
@ApiStatus.OverrideOnly
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.docker.ijent.DockerEelDescriptor",
  "com.intellij.platform.ijent.ssh.SshEelDescriptor",
  "com.intellij.platform.ijent.ssh.impl.IjentSshAgentForwardingService",
)
interface EelDescriptorWithSshForwardingEnabled : EelDescriptor {
  /** Silent by default, so that an environment which does not override it keeps the behaviour it had before consent existed. */
  val sshForwardingConsent: SshForwardingConsentPolicy get() = SshForwardingConsentPolicy.SILENT
}

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
 *
 * ## Type Consistency
 * Although not expressible in the static type system, an [EelMachine], its corresponding [EelDescriptor]s, and the `Eel*Api` instances
 * it produces always share the same backend and operating system characteristics:
 * - If an [EelMachine] is backed by IJent, its [EelDescriptor]s belong to IJent and all `Eel*Api` instances implement the corresponding
 *   `Ijent*Api` interfaces (and similarly for [com.intellij.platform.eel.provider.LocalEelMachine] and [LocalEelApi]).
 * - If a descriptor, machine, or API is for Windows (or POSIX), [EelDescriptor.osFamily] is [EelOsFamily.Windows] (or [EelOsFamily.Posix])
 *   and all `Eel*Api` instances implement the corresponding `Eel*WindowsApi` (or `Eel*PosixApi`) interfaces.
 */
@ApiStatus.Experimental
interface EelMachine {
  /**
   * Returns true if [toEelApi] was successfully called and if there is no evidence of connection failure.
   * In this case, [toEelApi] will unlikely perform any I/O under the hood.
   *
   * If it returns false, then call of [toEelApi] will certainly do a lot of I/O under the hood.
   */
  val isConnected: Boolean

  @get:ApiStatus.Internal
  val internalName: String

  /**
   * It is recommended but not obliged to return the same info as in [EelDescriptor.name].
   */
  @Suppress("HardCodedStringLiteral")
  @get:ApiStatus.Experimental
  val name: @Nls String get() = "TODO OVERRIDE ME"

  /**
   * Converts this machine into a [EelApi] — starts or reuses a running environment without binding a lease to a caller scope.
   *
   * The machine selects the connection. [descriptor] defines the path namespace of the API and all paths it returns.
   * The caller must ensure that [descriptor] addresses this environment, including when another resolver owns that descriptor.
   * Implementations can reject path namespaces they do not support.
   *
   * To tie the environment's lifecycle to a [CoroutineScope] lease, use the [toEelApi] overload taking `lifetime`.
   *
   * @throws EelUnavailableException if eel is unavailable (i.e. remote machine is gone, docker container removed e.t.c.). Show it to a user, ask to fix and try again.
   */
  @ApiStatus.Experimental
  @Throws(EelUnavailableException::class)
  suspend fun toEelApi(descriptor: EelDescriptor): EelApi

  /**
   * Acquires a reference-counted connection lease on this machine bound to [lifetime] and returns its [EelApi] for [descriptor].
   *
   * Unlike [toEelApi] without a scope, this overload ties the environment's lifecycle to [lifetime]. The lease remains active until
   * [lifetime] is cancelled or completes. When all leases on this machine (including any open IDE projects) end, its machine-bound
   * [fileSystem] and underlying remote agent session are closed.
   *
   * Why a [lifetime] parameter instead of making [EelApi] implement [AutoCloseable]: an [EelApi] is backed by a machine-wide shared
   * session, and most callers of the single-parameter [toEelApi] are one-shot lookups that never close the returned instance. Taking
   * [lifetime] at acquisition time distinguishes lease-holding callers from fire-and-forget callers, so reference counts cannot leak
   * and closing one caller's scope never terminates a session still used by another lease or an open project.
   *
   * @throws EelUnavailableException if the environment cannot be reached.
   */
  @ApiStatus.Experimental
  @Throws(EelUnavailableException::class)
  suspend fun toEelApi(descriptor: EelDescriptor, lifetime: CoroutineScope): EelApi {
    return EelMachineConnectionProvider.registry.openConnection(this, descriptor, lifetime)
  }

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
// TODO VisibleForClasses can't restrict usage of this method.
@ApiStatus.Experimental
@ApiStatus.NonExtendable
@Deprecated("Now every EelMachine has this property. Stop checking for this property")
interface EelMachineWithConnectionState : EelMachine

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
   *
   * See also [EelMachine.name].
   */
  @get:ApiStatus.Experimental
  val name: @NonNls String

  /**
   * The platform of an environment corresponding to this [EelDescriptor].
   */
  @get:ApiStatus.Experimental
  val osFamily: EelOsFamily
}
