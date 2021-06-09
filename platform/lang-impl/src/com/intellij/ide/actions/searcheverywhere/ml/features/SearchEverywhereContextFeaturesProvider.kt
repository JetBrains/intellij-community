// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere.ml.features

import com.intellij.internal.statistic.local.ActionsGlobalSummaryManager
import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

internal class SearchEverywhereContextFeaturesProvider {
  companion object {
    private const val LOCAL_MAX_USAGE_COUNT_KEY = "maxUsage"
    private const val LOCAL_MIN_USAGE_COUNT_KEY = "minUsage"
    private const val GLOBAL_MAX_USAGE_COUNT_KEY = "globalMaxUsage"
    private const val GLOBAL_MIN_USAGE_COUNT_KEY = "globalMinUsage"

    private const val OPEN_FILE_TYPES_KEY = "openFileTypes"
    private const val LAST_ACTIVE_TOOL_WINDOW_KEY = "lastOpenToolWindow"
  }

  fun getContextFeatures(project: Project?): Map<String, Any> {
    val data = hashMapOf<String, Any>()
    val localTotalStats = ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java).getTotalStats()
    val globalTotalStats = ApplicationManager.getApplication().getService(ActionsGlobalSummaryManager::class.java).totalSummary
    data[LOCAL_MAX_USAGE_COUNT_KEY] = localTotalStats.maxUsageCount
    data[LOCAL_MIN_USAGE_COUNT_KEY] = localTotalStats.minUsageCount
    data[GLOBAL_MAX_USAGE_COUNT_KEY] = globalTotalStats.maxUsageCount
    data[GLOBAL_MIN_USAGE_COUNT_KEY] = globalTotalStats.minUsageCount

    project?.let {
      // report tool windows' ids
      val twm = ToolWindowManager.getInstance(project)
      var id: String? = null
      ApplicationManager.getApplication().invokeAndWait {
        id = twm.lastActiveToolWindowId
      }

      id?.let { toolwindowId ->
        data[LAST_ACTIVE_TOOL_WINDOW_KEY] = toolwindowId
      }

      // report types of open files in editor: fileType -> amount
      val fem = FileEditorManager.getInstance(it)
      data[OPEN_FILE_TYPES_KEY] = fem.openFiles.map { file -> file.fileType.name }.distinct()
    }
    return data
  }
}