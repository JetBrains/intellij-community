// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl.canBeSelected

import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class ScratchCanBeSelectedInProjectPaneProvider : CanBeSelectedInProjectPaneProvider {
  override fun isSupported(project: Project, virtualFile: VirtualFile): Boolean {
    return ScratchUtil.isScratch(virtualFile) &&
           ProjectView.getInstance(project).isShowScratchesAndConsoles(ProjectViewPane.ID)
  }
}