// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.PackageIndex
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiPackage
import com.intellij.refactoring.modplan.ModPlanOptionValues
import com.intellij.refactoring.modplan.move.ModPlanMoveOptions
import com.intellij.testFramework.ModPlanMoveTestSupport
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.extension.ExtendWith

@TestApplication
@ExtendWith(KnownGapsExtension::class)
class MovePackageMultirootOnModPlanTest : MovePackageMultirootTest() {
  override fun move(elements: Array<PsiElement>, targetPackage: PsiPackage, searchInComments: Boolean, searchInNonJavaFiles: Boolean) {
    val project = elements.first().project
    val options = ModPlanOptionValues.EMPTY
      .with(ModPlanMoveOptions.SEARCH_IN_COMMENTS, searchInComments)
      .with(ModPlanMoveOptions.SEARCH_FOR_TEXT, searchInNonJavaFiles)
    val requests = if (elements.all { it is PsiPackage }) {
      val firstDirectory = (elements.first() as PsiPackage).directories.first()
      listOf(sourceRootOf(firstDirectory) to elements.toList())
    }
    else {
      elements.groupBy { sourceRootOf(it.containingFile) }.map { [root, group] -> root to ModPlanMoveTestSupport.filesOf(group) }
    }
    for ((root, subjects) in requests) {
      ModPlanMoveTestSupport.moveOnModPlan(project, subjects, directoryUrlIn(project, root, targetPackage.qualifiedName), options)
    }
  }

  private fun sourceRootOf(item: PsiFileSystemItem): VirtualFile {
    return ProjectFileIndex.getInstance(item.project).getSourceRootForFile(item.virtualFile)!!
  }

  private fun directoryUrlIn(project: Project, root: VirtualFile, packageName: String): String {
    val rootPackage = PackageIndex.getInstance(project).getPackageNameByDirectory(root).orEmpty()
    val relative = packageName.removePrefix(rootPackage).removePrefix(".")
    return if (relative.isEmpty()) root.url else root.url + "/" + relative.replace('.', '/')
  }
}