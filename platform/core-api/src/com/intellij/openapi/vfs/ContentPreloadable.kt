// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import org.jetbrains.annotations.ApiStatus

/**
 * Optional capability for VirtualFile implementations that can asynchronously preload their content
 * into a local cache to avoid subsequent blocking operations.
 */
@ApiStatus.Internal
interface ContentPreloadable {
  /** Preload content asynchronously. Implementations should be idempotent and fast to no-op if already loaded. */
  suspend fun preloadContent()
}
