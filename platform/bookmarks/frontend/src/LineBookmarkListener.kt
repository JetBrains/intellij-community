// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bookmarks.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.impl.editorId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.bookmarks.rpc.BookmarksApi
import com.intellij.platform.project.projectId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities


@Service(Service.Level.PROJECT)
private class LineBookmarkListener(private val project: Project, val coroutineScope: CoroutineScope) : EditorMouseListener {

  private val MouseEvent.isUnexpected
    get() = !SwingUtilities.isLeftMouseButton(this) || isPopupTrigger || if (SystemInfo.isMac) !isMetaDown else !isControlDown

  private val EditorMouseEvent.isUnexpected
    get() = isConsumed || area != EditorMouseEventArea.LINE_MARKERS_AREA || mouseEvent.isUnexpected

  override fun mouseClicked(event: EditorMouseEvent) {
    if (event.isUnexpected) return
    event.editor.project?.let { if (it != project) return }
    coroutineScope.launch {
      BookmarksApi.getInstance().addBookmark(project.projectId(), event.editor.editorId(), event.logicalPosition.line)
    }
    event.consume()
  }

  init {
    if (!project.isDefault) {
      val multicaster = EditorFactory.getInstance().eventMulticaster
      multicaster.addEditorMouseListener(this, project)
    }
  }
}

private class LineBookmarkActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.serviceAsync<LineBookmarkListener>()
  }
}