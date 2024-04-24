// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.backend.compatibility

import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiManager

internal fun NavBarItem.isModuleContentRoot(): Boolean {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  if (this is PsiNavBarItem) {
    val psi = data
    if (psi is PsiDirectory) {
      val dir = psi.virtualFile
      return dir.parent == null || ProjectRootsUtil.isModuleContentRoot(dir, psi.project)
    }
  }
  return false
}

internal fun NavBarItem.psiDirectories(): List<PsiDirectory> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  when (this) {
    is PsiNavBarItem -> {
      val psi = data
      if (psi is PsiDirectory) {
        return listOf(psi)
      }
      if (psi is PsiDirectoryContainer) {
        return listOf(*psi.directories)
      }
      val dir = psi.containingFile?.containingDirectory
      if (dir != null) {
        return listOf(dir)
      }
      return emptyList()
    }
    is ModuleNavBarItem -> {
      val data = data
      val psiManager = PsiManager.getInstance(data.project)
      return ModuleRootManager.getInstance(data).sourceRoots.mapNotNull {
        psiManager.findDirectory(it)
      }
    }
    else -> {
      // TODO obtain directories from other NavBarItem implementations
      return emptyList()
    }
  }
}
