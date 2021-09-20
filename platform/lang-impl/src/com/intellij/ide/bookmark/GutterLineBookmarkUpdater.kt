// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager

internal class GutterLineBookmarkUpdater : EditorFactoryListener {

  override fun editorCreated(event: EditorFactoryEvent) {
    val file = FileDocumentManager.getInstance().getFile(event.editor.document) ?: return
    ApplicationManager.getApplication().executeOnPooledThread {
      ProjectManager.getInstanceIfCreated()?.openProjects?.forEach {
        (BookmarksManager.getInstance(it) as? BookmarksManagerImpl)?.refreshRenderers(file)
      }
    }
  }
}
