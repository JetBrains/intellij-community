// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.CalledInAny

internal class DiffEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    val title = getEditorTabName(project, file) ?: return null
    return shortenTitleIfNeeded(project, file, title)
  }

  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    if (file !is DiffVirtualFileWithTabName) {
      return null
    }

    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    val title = file.getEditorTabName(project, fileEditorManager.getEditorList(file)) ?: return null

    return shortenTitleIfNeeded(project, file, title)
  }

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): @NlsContexts.Tooltip String? {
    return getEditorTabName(project, virtualFile)
  }

  private fun shortenTitleIfNeeded(project: Project, file: VirtualFile, title: @NlsContexts.TabTitle String): @NlsContexts.TabTitle String =
    if (DiffEditorTabFilesManager.getInstance(project).isDiffOpenedInWindow(file)) title else title.shorten()

  private fun getEditorTabName(project: Project, file: VirtualFile): @NlsSafe String? {
    if (file !is DiffVirtualFileWithTabName) {
      return null
    }
    val editors = FileEditorManager.getInstance(project).getEditorList(file)
    return file.getEditorTabName(project, editors)
  }

  private fun String.shorten(maxLength: Int = 30): @NlsSafe String {
    if (length < maxLength) return this
    val index = indexOf('(')
    if (index in 1 until maxLength) return substring(0, index)

    return StringUtil.shortenTextWithEllipsis(this, maxLength, 0)
  }
}

interface DiffVirtualFileWithTabName {
  @CalledInAny
  fun getEditorTabName(project: Project, editors: List<FileEditor>): @NlsContexts.TabTitle String?
}
