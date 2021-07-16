// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.favoritesTreeView.FavoritesManager
import com.intellij.internal.statistic.local.FileTypeUsageLocalSummary
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

internal class SearchEverywhereFileFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    private const val FILETYPE_DATA_KEY = "fileType"
    private const val IS_FAVORITE_DATA_KEY = "isFavorite"
    private const val IS_OPENED_DATA_KEY = "isOpened"
    private const val RECENT_INDEX_DATA_KEY = "recentFilesIndex"
    private const val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = "timeSinceLastModification"
    private const val FILETYPE_USAGE_RATIO_DATA_KEY = "fileTypeUsageRatio"
    private const val FILETYPE_LAST_USAGE_DATA_KEY = "fileTypeLastUsage"
  }

  override fun getElementFeatures(element: Any,
                                  currentTime: Long,
                                  queryLength: Int): Map<String, Any> {
    if (element !is PSIPresentationBgRendererWrapper.PsiItemWithPresentation)
      return emptyMap()

    return getPsiElementFeatures(element.item, currentTime)
  }

  private fun getPsiElementFeatures(element: PsiElement, currentTime: Long): Map<String, Any> {
    val virtualFile = (element as PsiFileSystemItem).virtualFile
    val project = element.project

    return hashMapOf(
      IS_FAVORITE_DATA_KEY to isFavorite(virtualFile, project),
      IS_OPENED_DATA_KEY to isOpened(virtualFile, project),
      FILETYPE_DATA_KEY to element.containingFile.fileType,
      RECENT_INDEX_DATA_KEY to getRecentFilesIndex(virtualFile, project),
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to currentTime - element.containingFile.virtualFile.timeStamp,
    ) + getFileTypeStats(virtualFile)
  }

  private fun isFavorite(virtualFile: VirtualFile, project: Project): Boolean {
    val favoritesManager = FavoritesManager.getInstance(project)
    return ReadAction.compute<Boolean, Nothing> { favoritesManager.getFavoriteListName(null, virtualFile) != null }
  }

  private fun isOpened(virtualFile: VirtualFile, project: Project): Boolean {
    val openedFiles = FileEditorManager.getInstance(project).openFiles
    return virtualFile in openedFiles
  }

  private fun getRecentFilesIndex(virtualFile: VirtualFile, project: Project): Int {
    val historyManager = EditorHistoryManager.getInstance(project)
    val recentFilesList = historyManager.fileList

    val fileIndex = recentFilesList.indexOf(virtualFile)
    if (fileIndex == -1) {
      return fileIndex
    }

    // Give the most recent files the lowest index value
    return recentFilesList.size - fileIndex
  }

  private fun getFileTypeStats(virtualFile: VirtualFile): Map<String, Any> {
    val fileTypeName = virtualFile.fileType.name
    val localSummary = service<FileTypeUsageLocalSummary>()
    val allFileTypesStats = localSummary.getFileTypeStats()

    val totalUsage = allFileTypesStats.values.sumBy { it.usageCount }
    val stats = allFileTypesStats[fileTypeName]

    return if (stats == null) {
      hashMapOf(
        FILETYPE_USAGE_RATIO_DATA_KEY to 0.0,
        FILETYPE_LAST_USAGE_DATA_KEY to 0.0,
      )
    }
    else {
      hashMapOf(
        FILETYPE_USAGE_RATIO_DATA_KEY to stats.usageCount.toDouble() / totalUsage,
        FILETYPE_LAST_USAGE_DATA_KEY to stats.lastUsed
      )
    }
  }
}