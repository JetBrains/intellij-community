// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class WelcomeFilesLanguageSupport : ScratchFileCreationHelper() {
  override fun afterCreate(project: Project, context: Context, scratchFile: PsiFile) {
    if (WelcomeFilesRootType.Util.isWelcomeFile(project, scratchFile.virtualFile)) {
      configureProject(project)
    }
  }

  override fun afterLanguageChange(project: Project, files: Set<VirtualFile>) {
    for (file in files) {
      if (WelcomeFilesRootType.Util.isWelcomeFile(project, file)) {
        configureProject(project)
        return
      }
    }
  }

  protected abstract fun configureProject(project: Project)
}