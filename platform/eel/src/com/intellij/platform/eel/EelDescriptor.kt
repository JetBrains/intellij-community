// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.path.EelPath.OS

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
 * If you need to access the remote environment, you can use the method [upgrade], which can suspend for some time before returning a working instance of [EelApi]
 */
interface EelDescriptor {
  @Deprecated("Use platform instead", ReplaceWith("platform"))
  val operatingSystem: OS
    get() = when (platform) {
      is EelPlatform.Windows -> OS.WINDOWS
      is EelPlatform.Posix -> OS.UNIX
    }

  /**
   * The platform of an environment corresponding to this [EelDescriptor].
   */
  val platform: EelPlatform

  /**
   * Retrieves an instance of [EelApi] corresponding to this [EelDescriptor].
   * This method may run a container, so it could suspend for a long time.
   */
  suspend fun upgrade(): EelApi
}
