// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testDiscovery

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile

internal class TestDiscoveryVcsHelperImpl : TestDiscoveryVcsHelper {
  override fun getAffectedFiles(project: Project, changeListName: String): List<VirtualFile> {
    val changeListManager = ChangeListManager.getInstance(project)
    if (changeListName == "All") {
      return changeListManager.affectedFiles
    }

    val changeList = changeListManager.findChangeList(changeListName) ?: return emptyList()
    return changeList.changes.mapNotNull { it.afterRevision?.file?.virtualFile }
  }

  override fun getChangedTextRanges(project: Project, file: PsiFile): List<TextRange> =
    VcsFacade.getInstance().getChangedTextRanges(project, file)
}
