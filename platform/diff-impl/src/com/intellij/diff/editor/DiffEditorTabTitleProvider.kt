// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.diff.editor.DiffEditorTabFilesManager.Companion.isDiffOpenedInNewWindow
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.Nls

class DiffEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {

  override fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    val title = getTitle(project, file)

    return if (isDiffOpenedInNewWindow(file)) title else title?.shorten()
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

  private fun String.shorten(maxLength: Int = 30): @Nls String {
    if (length < maxLength) return this
    val index = indexOf('(')
    if (index in 1 until maxLength) return substring(0, index)

    return StringUtil.shortenTextWithEllipsis(this, maxLength, 0)
  }
}
