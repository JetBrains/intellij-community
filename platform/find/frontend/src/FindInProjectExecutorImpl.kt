// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.frontend

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import com.intellij.platform.find.FindInProjectModel
import com.intellij.find.impl.FindKey
import com.intellij.platform.find.FindRemoteApi
import com.intellij.platform.find.RdSimpleTextAttributes
import com.intellij.platform.find.SearchScopeProvider
import com.intellij.platform.find.UsageInfoModel
import com.intellij.platform.project.projectId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfoAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.table.TableCellRenderer

@ApiStatus.Internal
open class FindInProjectExecutorImpl(val coroutineScope: CoroutineScope) : FindInProjectExecutor() {

  override fun createTableCellRenderer(): TableCellRenderer? {
    return ThinClientFindInProjectTableCellRenderer()
  }

  override fun findUsages(
    project: Project,
    progressIndicator: ProgressIndicatorEx,
    presentation: FindUsagesProcessPresentation,
    findModel: FindModel,
    filesToScanInitially: Set<VirtualFile>,
    onResult: (UsageInfoAdapter) -> Boolean,
    onFinish: () -> Unit?
  ) {
    if (findModel.stringToFind.isBlank()) {
      return
    }


    if (FindKey.isEnabled) {
      println("findUsages started")
      coroutineScope.launch {
        FindRemoteApi.getInstance().findByModel(getModel(project, findModel)).collect { info ->
          val usage = UsageInfoModel(project, info, coroutineScope)

          println("found result ${usage.model.presentation.joinToString{ it.text }}")
          onResult(usage)
        }
        onFinish()
      }
      //TODO after launching coroutine scope should be some logic to check is it finished or not to show "Searching" or "Nothing found" states and progress indicator
    }
    else {
      super.findUsages(project, progressIndicator, presentation, findModel, filesToScanInitially, onResult, onFinish)
    }
  }
}

private fun getModel(project: Project, findModel: FindModel): FindInProjectModel {
  return FindInProjectModel(project.projectId(),
                            findModel.stringToFind,
                            findModel.isWholeWordsOnly,
                            findModel.isRegularExpressions,
                            findModel.isCaseSensitive,
                            findModel.isProjectScope,
                            findModel.fileFilter,
                            findModel.moduleName,
                            findModel.searchContext.name,
                            findModel.customScope?.let { SearchScopeProvider.getScopeId(it.displayName) }) //TODO rework
}

fun RdSimpleTextAttributes.toInstance(): SimpleTextAttributes {
  return SimpleTextAttributes(
    this.bgColor?.let { Color(it) },
    this.fgColor?.let { Color(it) },
    this.waveColor?.let { Color(it) },
    this.style
  )
}