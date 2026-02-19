// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.nodes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.workspaceModel.ide.ProjectRootEntity

internal fun getProjectRoots(project: Project): List<VirtualFile> {
  val projectViewHelper = ProjectViewDirectoryHelper.getInstance(project)
  return WorkspaceModel.getInstance(project).currentSnapshot.entities(ProjectRootEntity::class.java)
    .mapNotNull { it.root.virtualFile }
    .filter { !projectViewHelper.isFileUnderContentRoot(it) }
    .toList()
}
