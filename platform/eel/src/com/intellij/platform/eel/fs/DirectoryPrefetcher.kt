// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.path.EelPath
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Prefetches directory listings for multiple roots in a single streaming RPC.
 * Server walks each root concurrently with metadata, streaming (path, fileInfo) pairs.
 *
 * Implemented by IJent filesystem APIs where gRPC transport supports this optimization.
 * Non-IJent filesystem APIs do not implement this interface.
 *
 * Consumer checks: `if (eelApi.fs is DirectoryPrefetcher) { ... }`.
 */
@ApiStatus.Internal
interface DirectoryPrefetcher {
  suspend fun prefetchDirectories(roots: Collection<EelPath>): Flow<Pair<EelPath, EelFileInfo>>
}
