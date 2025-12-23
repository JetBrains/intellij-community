// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.canBeSelected

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

internal class SourceOrLibraryCanBeSelectedInProjectPaneProvider : CanBeSelectedInProjectPaneProvider {
  override fun isSupported(project: Project, virtualFile: VirtualFile): Boolean {
    val baseDir = project.getBaseDir()
    val index = ProjectRootManager.getInstance(project).getFileIndex()
    return index.getContentRootForFile(virtualFile, false) != null ||
           index.isInLibrary(virtualFile) ||
           (baseDir != null && VfsUtilCore.isAncestor(baseDir, virtualFile, false))
  }
}