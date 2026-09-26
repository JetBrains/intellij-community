// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

/**
 * An interface for the [com.intellij.openapi.vfs.VirtualFileSystem] implementations that supports asynchronous IO tasks
 * execution.
 *
 * Ideally, an asynchronous IO tasks execution should be an implementation detail of [com.intellij.openapi.vfs.VirtualFileSystem],
 * invisible from outside -- but a curious client may need something like 'fsync' or 'are there unfinished async ops' sometimes.
 *
 * This is expected to be used inside the platform, hence [ApiStatus.Internal].
 */
@ApiStatus.Internal
interface AsyncableFileSystem {
  fun hasUnfinishedTasks(): Boolean

  fun hasUnfinishedTasksFor(file: VirtualFile): Boolean

  @Throws(IOException::class)
  fun fsync()

  @Throws(IOException::class)
  fun fsync(file: VirtualFile)
}
