// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.impl

import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.ide.projectView.impl.ProjectRootsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.psi.*

internal fun NavBarItem.pathToItem(): List<NavBarItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return generateSequence(this) {
    it.findParent()
  }.toList().asReversed()
}

private fun NavBarItem.findParent(): NavBarItem? =
  NavBarItemProvider.EP_NAME
    .extensionList
    .firstNotNullOfOrNull { ext -> ext.findParent(this) }

internal fun NavBarItem.children(): List<NavBarItem> {
  ApplicationManager.getApplication().assertReadAccessAllowed()
  return iterateAllChildren().sortedWith(siblingsComparator)
}

private fun NavBarItem.iterateAllChildren(): Iterable<NavBarItem> =
  NavBarItemProvider.EP_NAME
    .extensionList
    .flatMap { ext -> ext.iterateChildren(this) }

private val weightComparator = compareBy<NavBarItem> { -it.weight() }
private val nameComparator = compareBy<NavBarItem, String>(NaturalComparator.INSTANCE) { it.presentation().text }
private val siblingsComparator = weightComparator.then(nameComparator)

private fun NavBarItem.weight() = when (this) {
  is ModuleNavBarItem -> 5
  is PsiNavBarItem -> when (data) {
    is PsiDirectoryContainer -> 4
    is PsiDirectory -> 4
    is PsiFile -> 2
    is PsiNamedElement -> 3
    else -> Int.MAX_VALUE
  }
  else -> Int.MAX_VALUE
}

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
      val file = psi.containingFile
      if (file != null) {
        return listOf(file.containingDirectory)
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
