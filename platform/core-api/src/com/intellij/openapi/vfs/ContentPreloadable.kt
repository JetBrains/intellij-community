// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import org.jetbrains.annotations.ApiStatus

/**
 * Optional capability for VirtualFile implementations that can asynchronously preload their content
 * into a local cache to avoid subsequent blocking operations.
 */
@ApiStatus.Internal
interface ContentPreloadable {
  /**
   * Asynchronously preloads the file content into a local cache.
   *
   * @param forceUpdate
   * - `false` (default): do nothing if content is already cached; must not perform I/O.
   * - `true`: refresh cached content from the backend, respecting any file-level restrictions.
   *
   * After preloading, content should be accessible via standard file access methods.
   */
  suspend fun preloadContent(forceUpdate: Boolean = false)
}
