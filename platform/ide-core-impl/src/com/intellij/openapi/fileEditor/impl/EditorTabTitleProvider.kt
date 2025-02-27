// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@OptIn(ExperimentalCoroutinesApi::class)
private val limitedDispatcher = Dispatchers.Default.limitedParallelism(2)

/**
 *
 * Provides custom name/tooltip for editor tab instead of filename/path.
 */
interface EditorTabTitleProvider : DumbAware {
  companion object {
    @JvmField
    @Internal
    val EP_NAME: ExtensionPointName<EditorTabTitleProvider> = ExtensionPointName("com.intellij.editorTabTitleProvider")
  }

  fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String?

  @Experimental
  suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    // Limit the parallelism of calculating the tab title.
    // Because it is quite extensively used when tabs are restored during project opening.
    // While implementations may perform long-running blocking operations, that may block all the threads of the coroutine pool.
    return withContext(limitedDispatcher) {
      blockingContext {
        getEditorTabTitle(project, file)
      }
    }
  }

  fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): @NlsContexts.Tooltip String? = null
}
