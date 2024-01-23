// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import kotlinx.coroutines.launch

private class GutterLineBookmarkUpdater : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val file = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return
    ((event.editor.project ?: ApplicationManager.getApplication()) as ComponentManagerEx).getCoroutineScope().launch {
      for (project in ProjectManagerEx.getOpenProjects()) {
        (BookmarksManager.getInstance(project) as? BookmarksManagerImpl)?.refreshRenderers(file)
      }
    }
  }
}
