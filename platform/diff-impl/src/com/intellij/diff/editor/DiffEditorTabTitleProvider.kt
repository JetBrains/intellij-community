// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

private class DiffEditorTabTitleProvider : EditorTabTitleProvider, DumbAware {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    val title = getEditorTabName(project, file) ?: return null
    if (DiffEditorTabFilesManager.getInstance(project).isDiffOpenedInWindow(file)) {
      return title
    }
    else {
      return title.shorten()
    }
  }

  override suspend fun getEditorTabTitleAsync(project: Project, file: VirtualFile): String? {
    if (file !is DiffVirtualFileWithTabName) {
      return null
    }

    val fileEditorManager = project.serviceAsync<FileEditorManager>()
    return withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      file.getEditorTabName(project, fileEditorManager.getEditorList(file))
    }
  }

  override fun getEditorTabTooltipText(project: Project, virtualFile: VirtualFile): @NlsContexts.Tooltip String? {
    return getEditorTabName(project, virtualFile)
  }

  private fun getEditorTabName(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    if (file !is DiffVirtualFileWithTabName) {
      return null
    }
    val supplier = {
      val editors = FileEditorManager.getInstance(project).getEditorList(file)
      file.getEditorTabName(project, editors)
    }
    if (EDT.isCurrentThreadEdt()) {
      return supplier()
    }

    @Suppress("DEPRECATION")
    val future = (project as ComponentManagerEx).getCoroutineScope().async(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      blockingContext {
        supplier()
      }
    }.asCompletableFuture()
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future)
  }

  private fun String.shorten(maxLength: Int = 30): @Nls String {
    if (length < maxLength) return this
    val index = indexOf('(')
    if (index in 1 until maxLength) return substring(0, index)

    return StringUtil.shortenTextWithEllipsis(this, maxLength, 0)
  }
}

interface DiffVirtualFileWithTabName {
  fun getEditorTabName(project: Project, editors: List<FileEditor>): @NlsContexts.TabTitle String?
}
