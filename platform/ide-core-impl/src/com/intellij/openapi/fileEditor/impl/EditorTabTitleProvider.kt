// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

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
    return blockingContext {
      getEditorTabTitle(project, file)
    }
  }

  fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): @NlsContexts.Tooltip String? = null
}
