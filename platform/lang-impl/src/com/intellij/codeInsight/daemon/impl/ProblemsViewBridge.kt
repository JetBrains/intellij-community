// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface ProblemsViewBridge {
  fun toggleCurrentFileProblems(project: Project, virtualFile: VirtualFile?, document: Document?)

  fun selectHighlighterIfVisible(project: Project, highlighter: RangeHighlighterEx)

  companion object {
    private val EP_NAME: ExtensionPointName<ProblemsViewBridge> = ExtensionPointName("com.intellij.problemsViewBridge")

    @JvmStatic
    fun getToolWindowId(): String = ToolWindowId.PROBLEMS_VIEW

    @JvmStatic
    fun getToolWindow(project: Project): ToolWindow? {
      return if (project.isDisposed) null else ToolWindowManager.getInstance(project).getToolWindow(getToolWindowId())
    }

    @JvmStatic
    fun toggleCurrentFileProblemsIfAvailable(project: Project, virtualFile: VirtualFile?, document: Document?) {
      EP_NAME.extensionList.firstOrNull()?.toggleCurrentFileProblems(project, virtualFile, document)
    }

    @JvmStatic
    fun selectHighlighterIfVisibleIfAvailable(project: Project, highlighter: RangeHighlighterEx) {
      EP_NAME.extensionList.firstOrNull()?.selectHighlighterIfVisible(project, highlighter)
    }
  }
}
