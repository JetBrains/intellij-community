// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.canBeSelected

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex

internal class ArchiveCanBeSelectedInProjectPaneProvider : CanBeSelectedInProjectPaneProvider {
  override fun isSupported(project: Project, virtualFile: VirtualFile): Boolean {
    val archiveFile = (virtualFile.getFileSystem() as? ArchiveFileSystem)?.getLocalByEntry(virtualFile) ?: return false
    return WorkspaceFileIndex.getInstance(project).getContentFileSetRoot(archiveFile, false) != null
  }
}