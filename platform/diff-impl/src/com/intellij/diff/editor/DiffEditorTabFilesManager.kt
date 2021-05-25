// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface DiffEditorTabFilesManager {
  fun showDiffFile(diffFile: ChainDiffVirtualFile, focusEditor: Boolean): Array<out FileEditor>

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DiffEditorTabFilesManager = project.service()
  }
}

class DefaultDiffTabFilesManager(private val project: Project) : DiffEditorTabFilesManager {

  override fun showDiffFile(diffFile: ChainDiffVirtualFile, focusEditor: Boolean): Array<out FileEditor> {
    return FileEditorManager.getInstance(project).openFile(diffFile, true)
  }
}
