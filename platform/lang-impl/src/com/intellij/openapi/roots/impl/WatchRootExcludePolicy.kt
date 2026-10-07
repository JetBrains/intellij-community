// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.SystemIndependent

/**
 * Excludes the watch root from being watched by the file watcher ([com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl]).
 * Implementations should refresh the file manually (see [com.intellij.openapi.vfs.newvfs.RefreshQueue]).
 */
@ApiStatus.Internal
interface WatchRootExcludePolicy {
  fun isApplicable(project: Project): Boolean

  fun shouldExclude(watchRootPath: @SystemIndependent String): Boolean

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<WatchRootExcludePolicy> = ExtensionPointName("com.intellij.watchRootExcludePolicy")
  }
}
