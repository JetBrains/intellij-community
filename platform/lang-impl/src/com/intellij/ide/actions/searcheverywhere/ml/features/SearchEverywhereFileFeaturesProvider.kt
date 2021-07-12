// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

internal class SearchEverywhereFileFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    private const val FILETYPE_DATA_KEY = "filetype"
    private const val IS_IGNORED_DATA_KEY = "isIgnored"
    private const val IS_CHANGED_DATA_KEY = "isChanged"
    private const val IS_FAVORITE_DATA_KEY = "isFavorite"
    private const val IS_OPENED_DATA_KEY = "isOpened"
    private const val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = "timeSinceLastModification"
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int): Map<String, Any> {
    if (element !is PSIPresentationBgRendererWrapper.PsiItemWithPresentation)
      return emptyMap()

    return getPsiElementFeatures(element.item, currentTime)
  }

  private fun getPsiElementFeatures(element: PsiElement, currentTime: Long): Map<String, Any> {
    val psiFile = element.containingFile
    val virtualFile = psiFile.virtualFile
    val project = psiFile.project

    val data = hashMapOf(
      IS_IGNORED_DATA_KEY to isIgnoredFile(virtualFile, project),
      IS_CHANGED_DATA_KEY to isChangedFile(virtualFile, project),
      IS_FAVORITE_DATA_KEY to isFavorite(virtualFile, project),
      IS_OPENED_DATA_KEY to isOpened(virtualFile, project),
      FILETYPE_DATA_KEY to element.containingFile.fileType,
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - element.containingFile.virtualFile.timeStamp,
    )

    return data
  }

  private fun isIgnoredFile(virtualFile: VirtualFile, project: Project): Boolean {
    val changeListManager = ChangeListManager.getInstance(project)
    return changeListManager.isIgnoredFile(virtualFile)
  }

  private fun isChangedFile(virtualFile: VirtualFile, project: Project): Boolean {
    val changeListManager = ChangeListManager.getInstance(project)
    return changeListManager.isFileAffected(virtualFile)
  }

  private fun isFavorite(virtualFile: VirtualFile, project: Project): Boolean {
    val favoritesManager = FavoritesManager.getInstance(project)
    return favoritesManager.getFavoriteListName(null, virtualFile) != null
  }

  private fun isOpened(virtualFile: VirtualFile, project: Project): Boolean {
    val openedFiles = FileEditorManager.getInstance(project).openFiles
    return virtualFile in openedFiles
  }
}