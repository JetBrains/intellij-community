// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.psi.PsiElement

internal class SearchEverywhereFileFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    private const val FILETYPE_DATA_KEY = "filetype"
    private const val IS_IGNORED_DATA_KEY = "isIgnored"
    private const val IS_CHANGED_DATA_KEY = "isChanged"

    private const val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = "timeSinceLastModification"
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int,
                                  localSummary: ActionsLocalSummary,
                                  globalSummary: ActionsGlobalSummaryManager): Map<String, Any> {
    if (element !is PsiElement)
      return emptyMap()

    return getPsiElementFeatures(element, currentTime)
  }

  private fun getPsiElementFeatures(element: PsiElement, currentTime: Long): Map<String, Any> {
    val data = hashMapOf(
      IS_IGNORED_DATA_KEY to isIgnoredFile(element),
      IS_CHANGED_DATA_KEY to isChangedFile(element),
      FILETYPE_DATA_KEY to element.containingFile.fileType,
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - element.containingFile.virtualFile.timeStamp,
    )

    return data
  }

  private fun isIgnoredFile(element: PsiElement): Boolean {
    val psiFile = element.containingFile
    val virtualFile = psiFile.virtualFile
    val project = element.containingFile.project

    val changeListManager = ChangeListManager.getInstance(project)
    return changeListManager.isIgnoredFile(virtualFile)
  }

  private fun isChangedFile(element: PsiElement): Boolean {
    val psiFile = element.containingFile
    val virtualFile = psiFile.virtualFile
    val project = element.containingFile.project

    val changeListManager = ChangeListManager.getInstance(project)
    return changeListManager.isFileAffected(virtualFile)
  }
}