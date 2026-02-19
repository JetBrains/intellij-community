// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

interface DiffEditorTabFilesManager {

  fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    DiffEditorTabFilesUtil.setForceOpeningsInNewWindow(diffFile, null)
    return showDiffFile(diffFile, focusEditor, DiffEditorTabFilesUtil.isDiffInEditor)
  }

  fun showDiffFile(diffFile: VirtualFile, focusEditor: Boolean, openInEditor: Boolean): Array<out FileEditor>

  /**
   * Returns true for diff files that are opened in their own window using
   * [com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl.SINGLETON_EDITOR_IN_WINDOW] options
   */
  fun isDiffOpenedInWindow(file: VirtualFile): Boolean

  companion object {

    @JvmStatic
    fun getInstance(project: Project): DiffEditorTabFilesManager = project.service()
  }
}
