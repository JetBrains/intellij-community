// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.backend

import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.FindPopupPanel
import com.intellij.find.impl.getPresentableFilePath
import com.intellij.find.replaceInProject.ReplaceInProjectManager
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.ui.toSerializableTextChunk
import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.platform.find.FindInFilesResult
import com.intellij.platform.find.FindRemoteApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private val LOG: Logger = logger<FindRemoteApiImpl>()

internal class FindRemoteApiImpl : FindRemoteApi {

  override suspend fun findByModel(findModel: FindModel, projectId: ProjectId, filesToScanInitially: List<VirtualFileId>, maxUsagesCount: Int): Flow<FindInFilesResult> {
    val sentItems = AtomicInteger(0)
    return channelFlow {
      //TODO rewrite find function without using progress indicator and presentation
      val progressIndicator = EmptyProgressIndicator()
      val presentation = FindUsagesProcessPresentation(UsageViewPresentation())

      val isReplaceState = findModel.isReplaceState
      val previousResult = ThreadLocal<UsageInfo2UsageAdapter>()
      val usagesCount = AtomicInteger()

      val project = projectId.findProjectOrNull()
      if (project == null) {
        LOG.warn("Project not found for id ${projectId}")
        return@channelFlow
      }
      val filesToScanInitially = filesToScanInitially.mapNotNull { it.virtualFile() }.toSet()
      // SearchScope is not serializable, so we will get it by id from the client
      setCustomScopeById(project, findModel)
      //read action is necessary in case of the loading from a directory
      val scope = readAction { FindInProjectUtil.getGlobalSearchScope(project, findModel) }
      FindInProjectUtil.findUsages(findModel, project, progressIndicator, presentation, filesToScanInitially) { usageInfo ->
        val usageNum = usagesCount.incrementAndGet()
        if (usageNum > maxUsagesCount) {
          return@findUsages false
        }
        val virtualFile = usageInfo.virtualFile
        if (virtualFile == null)
          return@findUsages true

        val adapter = UsageInfo2UsageAdapter(usageInfo)
        val previousItem: UsageInfo2UsageAdapter? = previousResult.get()
        if (!isReplaceState && previousItem != null) adapter.merge(previousItem)
        previousResult.set(adapter)
        adapter.updateCachedPresentation()
        val textChunks = adapter.text.map {
          it.toSerializableTextChunk()
        }
        val bgColor = VfsPresentationUtil.getFileBackgroundColor(project, virtualFile)?.rpcId()
        val presentablePath = getPresentableFilePath(project, scope, virtualFile)

        val result = FindInFilesResult(
          presentation = textChunks,
          line = adapter.line,
          navigationOffset = adapter.navigationOffset,
          mergedOffsets = adapter.mergedInfos.map { it.navigationOffset },
          length = adapter.navigationRange.endOffset - adapter.navigationRange.startOffset,
          fileId = virtualFile.rpcId(),
          presentablePath =
            if (virtualFile.parent == null) FindPopupPanel.getPresentablePath(project, virtualFile) ?: virtualFile.presentableUrl
            else FindPopupPanel.getPresentablePath(project, virtualFile.parent) + File.separator + virtualFile.name
          ,
          shortenPresentablePath = presentablePath,
          backgroundColor = bgColor,
          tooltipText = adapter.tooltipText,
          iconId = adapter.icon?.rpcId(),
        )

        launch {
          send(result)
          if (sentItems.incrementAndGet() >= maxUsagesCount) {
            close()
          }
        }

        usagesCount.get() <= maxUsagesCount
      }
    }
  }

  override suspend fun performFindAllOrReplaceAll(findModel: FindModel, openInNewTab: Boolean, projectId: ProjectId) {
    val project = projectId.findProjectOrNull()
    val findSettings = FindSettings.getInstance()
    val separateViewSaved = findSettings.isShowResultsInSeparateView
    findSettings.isShowResultsInSeparateView = openInNewTab
    if (project == null) {
      LOG.warn("Project not found for id ${projectId}. FindAll/ReplaceAll operation skipped")
      return
    }
    setCustomScopeById(project, findModel)
    if (findModel.isReplaceState) {
      ReplaceInProjectManager.getInstance(project).replaceInPath(findModel)
    }
    else {
      FindInProjectManager.getInstance(project).findInPath(findModel)
    }
    findSettings.isShowResultsInSeparateView = separateViewSaved
  }

  override suspend fun checkDirectoryExists(findModel: FindModel): Boolean {
    return FindInProjectUtil.getDirectory(findModel) != null
  }

  private fun setCustomScopeById(project: Project, findModel: FindModel) {
    if (findModel.customScope == null && findModel.isCustomScope) {
      val scopeId = findModel.customScopeId ?: return
      ScopesStateService.getInstance(project).getScopeById(scopeId)?.let {
        findModel.customScope = it
      }
    }
  }
}