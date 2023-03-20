// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project

class FolderNode(project: Project, bookmark: FileBookmark) : BookmarkNode<FileBookmark>(project, bookmark) {

  override fun getVirtualFile() = value?.file
  override fun getChildren() = computeDirectoryChildren()

  override fun update(presentation: PresentationData) {
    val file = virtualFile ?: return
    when (val scratch = computeScratchPresentation(file)) {
      null -> super.update(presentation)
      else -> {
        presentation.setIcon(wrapIcon(scratch.second ?: findFileIcon()))
        addTextTo(presentation, scratch.first)
      }
    }
  }
}
