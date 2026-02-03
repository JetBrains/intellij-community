// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.canBeSelected

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem

internal class ArchiveCanBeSelectedInProjectPaneProvider : CanBeSelectedInProjectPaneProvider {
  override fun isSupported(project: Project, virtualFile: VirtualFile): Boolean {
    val archiveFile = (virtualFile.getFileSystem() as? ArchiveFileSystem)?.getLocalByEntry(virtualFile) ?: return false
    val index = ProjectRootManager.getInstance(project).getFileIndex()
    return index.getContentRootForFile(archiveFile, false) != null
  }
}