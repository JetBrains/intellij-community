// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.selection

import com.intellij.ide.projectView.ProjectPaneSelectableFileContributor
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ScratchProjectPaneSelectableFileContributor : ProjectPaneSelectableFileContributor {
  override fun isSelectable(project: Project, virtualFile: VirtualFile): Boolean {
    return ScratchUtil.isScratch(virtualFile) &&
           ProjectView.getInstance(project).isShowScratchesAndConsoles(ProjectViewPane.ID)
  }
}