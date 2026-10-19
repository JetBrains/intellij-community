// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/**
 * Registers project-scoped bulk VFS listeners.
 *
 *
 * Listeners are invoked on a background thread. Registrations are removed when their parent disposable is disposed or when
 * their coroutine scope completes.
 */
@ApiStatus.Experimental
interface ProjectBulkFileListenerRegistry {
  fun addListener(listener: ProjectBulkFileListener, parentDisposable: Disposable)

  fun addListener(coroutineScope: CoroutineScope, listener: ProjectBulkFileListener)

  companion object {
    fun getInstance(project: Project): ProjectBulkFileListenerRegistry {
      return project.service<ProjectBulkFileListenerRegistry>()
    }
  }
}
