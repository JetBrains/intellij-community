// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath.OS
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
 * A descriptor of an environment where [EelApi] may exist.
 *
 * ## Examples
 * 1. There is a singleton [LocalEelDescriptor] which always exists, and it denotes the environment where the IDE runs
 * 2. On Windows, there can be [EelDescriptor] that corresponds to a WSL distribution.
 * Each distribution gives rise to a unique [EelDescriptor]
 * 3. Each separate Docker container has its own [EelDescriptor]
 * 4. Each SSH host has its own [EelDescriptor]
 *
 * ## Purpose
 * [EelDescriptor] is a marker of an environment, that is
 * - **Lightweight**: it is opposed to [EelApi], which is a heavy object that takes considerable amount of resources to initialize.
 * While it is not free to obtain [EelDescriptor] (i.e., you may need to interact with WSL services and Docker daemon), it is much cheaper than
 * preparing an environment for deep interaction (i.e., running a WSL Distribution or a Docker container).
 * - **Durable**: There is no guarantee that an instance of [EelApi] would be alive for a long time.
 * For example, an SSH connection can be interrupted, and a Docker container can be restarted. These events do not affect the lifetime of [EelDescriptor].
 *
 * ## Usage
 * The intended way to obtain [EelDescriptor] is with the use of `getEelDescriptor`:
 * ```kotlin
 * Path.of("\\\\wsl.localhost\\Ubuntu\\home\\Jacob\\projects").getEelDescriptor()
 * project.getEelDescriptor()
 * ```
 *
 * You are free to compare and store [EelDescriptor].
 * TODO: In the future, [EelDescriptor] may also be serializable.
 * If you need to access the remote environment, you can use the method [toEelApi], which can suspend for some time before returning a working instance of [EelApi]
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
   * Describes Eel in a user-readable manner, i.e: "Docker: <container_name>" or "Wsl: <distro name>".
   * Format is *not* specified, but guaranteed to be user-readable.
   */
  @get:ApiStatus.Internal
  val userReadableDescription: @NonNls String

  /**
   * The platform of an environment corresponding to this [EelDescriptor].
   */
  @get:ApiStatus.Experimental
  val osFamily: EelOsFamily

  @ApiStatus.Experimental
  suspend fun toEelApi(): EelApi

  /**
   * Retrieves an instance of [EelApi] corresponding to this [EelDescriptor].
   * This method may run a container, so it could suspend for a long time.
   */
  @Deprecated("Use toEelApi() instead", replaceWith = ReplaceWith("toEelApi()"))
  @ApiStatus.Internal
  suspend fun upgrade(): EelApi = toEelApi()
}
