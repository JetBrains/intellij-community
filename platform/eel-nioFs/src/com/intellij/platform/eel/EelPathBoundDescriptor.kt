// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel

import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

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
