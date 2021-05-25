// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

class DiffEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {

  override fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    return getTitle(project, file)
  }

  override fun getEditorTabTooltipText(project: Project, file: VirtualFile): @NlsContexts.Tooltip String? {
    return getTitle(project, file)
  }

  private fun getTitle(project: Project, file: VirtualFile): @Nls String? {
    if (file !is ChainDiffVirtualFile) return null

    return FileEditorManager.getInstance(project)
      .getSelectedEditor(file)
      ?.let { it as? DiffRequestProcessorEditor }
      ?.processor?.activeRequest?.title
  }
}
