// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * Batched metadata requests that fill a [com.intellij.platform.eel.PrefetchDataElement].
 *
 * Implemented by the IJent file system APIs, where the gRPC transport supports these requests.
 * The other file system APIs do not implement this interface.
 *
 * A consumer checks: `if (eelApi.fs is EelPrefetchApi) { ... }`.
 */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.eel.provider.PrefetchCache",
  "com.intellij.platform.eel.provider.PrefetchCacheKt",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApiImpl",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemWindowsApiImpl",
  "com.intellij.platform.ijent.fs.IjentFileSystemApi",
  "com.intellij.platform.ijent.functional.IjentApiErrorsTest",
  "com.intellij.platform.ijent.functional.TcpLocalBootstrapTest",
  "com.intellij.platform.ijent.functional.fs.IjentFileSystemTest",
)
interface EelPrefetchApi {
  /**
   * Walks each root with its metadata in one streaming RPC and emits `(path, info)` pairs.
   * The server walks the roots concurrently. The info does not follow a symlink, but it carries the link target.
   */
  suspend fun prefetchDirectories(roots: Collection<EelPath>): Flow<Pair<EelPath, EelFileInfo>>

  /**
   * Stats every path in one RPC. A path that does not exist maps to [PrefetchedStat.Absent].
   * A path with another error has no entry.
   */
  suspend fun statMany(paths: Collection<EelPath>): Map<EelPath, PrefetchedStat>
}

/** The result of a stat cached on its own. It says nothing about a sibling of the path. */
@ApiStatus.Internal
@VisibleToClasses(
  "com.intellij.platform.eel.provider.PrefetchCache",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemPosixApiImpl",
  "com.intellij.platform.ijent.community.impl.IjentFailSafeFileSystemWindowsApiImpl",
  "com.intellij.platform.ijent.functional.fs.IjentFileSystemTest",
  "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemApiDelegate",
  "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemPosixApi",
  "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemPosixApiPrefetchTest",
  "com.intellij.platform.ijent.impl.base.GrpcIjentFileSystemWindowsApi",
)
sealed interface PrefetchedStat {
  /**
   * [info] does not follow a symlink, but it carries the link target.
   * [target] is the info after the symlinks are followed. It is [info] for a path that is not a symlink, and null for a broken link.
   */
  class Present(val info: EelFileInfo, val target: EelFileInfo?) : PrefetchedStat

  object Absent : PrefetchedStat

  companion object {
    /** A present path with [info]. A symlink has no known target. */
    fun of(info: EelFileInfo): Present {
      val target = when (info.type) {
        is EelPosixFileInfo.Type.Symlink -> null
        is EelFileInfo.Type.Directory, is EelFileInfo.Type.Regular, is EelFileInfo.Type.Other -> info
      }
      return Present(info, target)
    }
  }
}
