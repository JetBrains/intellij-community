// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider

import com.intellij.platform.eel.EelDescriptor
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * SPI for resolving [EelDescriptor] from NIO paths that go through multi-routing filesystem.
 *
 * This abstraction decouples `eel-nioFs` from direct dependency on `core-nio-fs` module
 * (`MultiRoutingFsPath`, `RoutingAwareFileSystemProvider`).
 *
 * Implementation is loaded via [ServiceLoader] from `eel-nioFs-impl`.
 */
@ApiStatus.Internal
interface EelNioFsBackend {
  companion object {
    val instance: EelNioFsBackend? by lazy {
      ServiceLoader.load(EelNioFsBackend::class.java, EelNioFsBackend::class.java.classLoader).firstOrNull()
    }
  }

  /**
   * Resolves the [EelDescriptor] for a given NIO path by unwrapping multi-routing layers.
   * Returns null if the path is a regular local path (not managed by multi-routing filesystem).
   */
  fun resolveDescriptor(path: Path): EelDescriptor?

  /**
   * Lightweight check: whether the path's filesystem provider supports routing.
   * This is a fast O(1) check (just `instanceof`) without path resolution overhead.
   */
  fun isRoutingAware(path: Path): Boolean
}
