// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.filePrediction.features.history.FileHistoryManagerWrapper
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
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.Time.*

internal class SearchEverywhereFileFeaturesProvider : SearchEverywhereElementFeaturesProvider() {
  companion object {
    private const val IS_DIRECTORY_DATA_KEY = "isDirectory"
    private const val FILETYPE_DATA_KEY = "fileType"
    private const val IS_FAVORITE_DATA_KEY = "isFavorite"
    private const val IS_OPENED_DATA_KEY = "isOpened"
    private const val RECENT_INDEX_DATA_KEY = "recentFilesIndex"
    private const val PREDICTION_SCORE_DATA_KEY = "predictionScore"

    private const val FILETYPE_USAGE_RATIO_DATA_KEY = "fileTypeUsageRatio"
    private const val TIME_SINCE_LAST_FILETYPE_USAGE = "timeSinceLastFileTypeUsage"
    private const val FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY = "fileTypeUsedInLastMinute"
    private const val FILETYPE_USED_IN_LAST_HOUR_DATA_KEY = "fileTypeUsedInLastHour"
    private const val FILETYPE_USED_IN_LAST_DAY_DATA_KEY = "fileTypeUsedInLastDay"
    private const val FILETYPE_USED_IN_LAST_MONTH_DATA_KEY = "fileTypeUsedInLastMonth"

    private const val TIME_SINCE_LAST_MODIFICATION_DATA_KEY = "timeSinceLastModification"
    private const val WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY = "wasModifiedInLastMinute"
    private const val WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY = "wasModifiedInLastHour"
    private const val WAS_MODIFIED_IN_LAST_DAY_DATA_KEY = "wasModifiedInLastDay"
    private const val WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY = "wasModifiedInLastMonth"
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

    val data = hashMapOf<String, Any>(
      IS_FAVORITE_DATA_KEY to isFavorite(virtualFile, project),
      IS_DIRECTORY_DATA_KEY to virtualFile.isDirectory
    )

    if (virtualFile.isDirectory) {
      // Rest of the features are only applicable to files, not directories
      return data
    }

    data[IS_OPENED_DATA_KEY] = isOpened(virtualFile, project)
    data[FILETYPE_DATA_KEY] = virtualFile.fileType.name
    data[RECENT_INDEX_DATA_KEY] = getRecentFilesIndex(virtualFile, project)
    data[PREDICTION_SCORE_DATA_KEY] = getPredictionScore(virtualFile, project)
    data.putAll(getModificationTimeStats(virtualFile, currentTime))
    data.putAll(getFileTypeStats(virtualFile, project, currentTime))

    return data
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

  private fun getModificationTimeStats(virtualFile: VirtualFile, currentTime: Long): Map<String, Any> {
    val timeSinceLastMod = currentTime - virtualFile.timeStamp

    return hashMapOf(
      TIME_SINCE_LAST_MODIFICATION_DATA_KEY to timeSinceLastMod,
      WAS_MODIFIED_IN_LAST_MINUTE_DATA_KEY to (timeSinceLastMod <= MINUTE),
      WAS_MODIFIED_IN_LAST_HOUR_DATA_KEY to (timeSinceLastMod <= HOUR),
      WAS_MODIFIED_IN_LAST_DAY_DATA_KEY to (timeSinceLastMod <= DAY),
      WAS_MODIFIED_IN_LAST_MONTH_DATA_KEY to (timeSinceLastMod <= (4 * WEEK.toLong()))
    )
  }

  private fun getFileTypeStats(virtualFile: VirtualFile, project: Project, currentTime: Long): Map<String, Any> {
    val fileTypeName = virtualFile.fileType.name
    val localSummary = project.service<FileTypeUsageLocalSummary>()
    val allFileTypesStats = localSummary.getFileTypeStats()

    val totalUsage = allFileTypesStats.values.sumBy { it.usageCount }
    val stats = allFileTypesStats[fileTypeName]

    val timeSinceLastUsage = if (stats == null) Long.MAX_VALUE else currentTime - stats.lastUsed
    val usageRatio = if (stats == null) 0 else stats.usageCount.toDouble() / totalUsage

    return hashMapOf(
      FILETYPE_USAGE_RATIO_DATA_KEY to usageRatio,

      TIME_SINCE_LAST_FILETYPE_USAGE to timeSinceLastUsage,
      FILETYPE_USED_IN_LAST_MINUTE_DATA_KEY to (timeSinceLastUsage <= MINUTE),
      FILETYPE_USED_IN_LAST_HOUR_DATA_KEY to (timeSinceLastUsage <= HOUR),
      FILETYPE_USED_IN_LAST_DAY_DATA_KEY to (timeSinceLastUsage <= DAY),
      FILETYPE_USED_IN_LAST_MONTH_DATA_KEY to (timeSinceLastUsage <= (4 * WEEK.toLong()))
    )
  }

  private fun getPredictionScore(virtualFile: VirtualFile, project: Project): Double {
    val historyManagerWrapper = FileHistoryManagerWrapper.getInstance(project)
    return historyManagerWrapper.calcNextFileProbability(virtualFile)
  }
}