// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.backend

import com.intellij.find.FindModel
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.getPresentableFilePath
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.platform.find.FindInProjectModel
import com.intellij.platform.find.FindInProjectResult
import com.intellij.platform.find.FindRemoteApi
import com.intellij.platform.find.RdSimpleTextAttributes
import com.intellij.platform.find.RdTextChunk
import com.intellij.platform.project.findProject
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NotNull
import java.awt.Font
import java.util.concurrent.atomic.AtomicInteger

class FindRemoteApiImpl: FindRemoteApi {
  override suspend fun findByModel(model: FindInProjectModel): Flow<FindInProjectResult> {
    return channelFlow {
      val findModel = FindModel().apply {
        stringToFind = model.stringToFind
        isWholeWordsOnly = model.isWholeWordsOnly
        isRegularExpressions = model.isRegularExpressions
        isCaseSensitive = model.isCaseSensitive
        isProjectScope = model.isProjectScope
        fileFilter = model.fileFilter
        moduleName = model.moduleName
        directoryName = model.directoryName
        isWithSubdirectories = model.isWithSubdirectories
        searchContext = FindModel.SearchContext.valueOf(model.searchContext)
        isCustomScope = model.isCustomScope
        //customScope = model.customScopeId.getScope
        isReplaceState = model.isReplaceState
      }
      val project = model.projectId.findProject()
      //TODO rewrite find function without using progress indicator and presentation
      val progressIndicator = EmptyProgressIndicator()
      val presentation = FindUsagesProcessPresentation(UsageViewPresentation())
      val isReplaceState = findModel.isReplaceState
      val previousResultMap: ConcurrentHashMap<String, UsageInfo2UsageAdapter> = ConcurrentHashMap()
      val maxUsages = ShowUsagesAction.getUsagesPageSize()
      val usagesCount = AtomicInteger()
      coroutineScope {
        val files = mutableSetOf<String>()
        val scope = FindInProjectUtil.getGlobalSearchScope(project, findModel)
        FindInProjectUtil.findUsages(findModel, project, progressIndicator, presentation, emptySet()) { usageInfo ->
          val virtualFile = usageInfo.virtualFile
          if (virtualFile == null)
            return@findUsages true

          val usagesCountRes = usagesCount.incrementAndGet()

          val adapter = UsageInfo2UsageAdapter(usageInfo).also { it.updateCachedPresentation() }
          files.add(adapter.path)
          val previousItem: UsageInfo2UsageAdapter? = previousResultMap[adapter.path]
          val merged = !isReplaceState && previousItem != null && adapter.merge(previousItem)
          adapter.updateCachedPresentation()
          previousResultMap[adapter.path] = adapter

          val textChunks = adapter.text.map {
            val attributes = createSimpleTextAttributes(it)
            RdTextChunk(it.text, attributes)
          }
          val bgColor = VfsPresentationUtil.getFileBackgroundColor(project, virtualFile)?.rpcId()
          val presentablePath = getPresentableFilePath(project, scope, virtualFile)

          val result = FindInProjectResult(
            presentation = textChunks,
            line = adapter.line + 1,
            offset = adapter.navigationOffset,
            length = adapter.navigationRange.endOffset - adapter.navigationRange.startOffset,
            fileId = virtualFile.rpcId(),
            path = virtualFile.path,
            presentablePath = presentablePath,
            merged = merged,
            backgroundColor = bgColor,
            usagesCount = usagesCountRes,
            fileCount = files.size,
          )

          launch {
            send(result)
          }
          usagesCount.get() < maxUsages
        }
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }
}

  private fun createSimpleTextAttributes(textChunk: @NotNull TextChunk): RdSimpleTextAttributes {
    var at = textChunk.simpleAttributesIgnoreBackground
    if (at.fontStyle == Font.BOLD) {
      at = SimpleTextAttributes(
        null, at.fgColor, at.waveColor,
        at.style and SimpleTextAttributes.STYLE_BOLD.inv() or
          SimpleTextAttributes.STYLE_SEARCH_MATCH
      )
    }

    return at.toModel()
  }

  fun SimpleTextAttributes.toModel(): RdSimpleTextAttributes {
    return RdSimpleTextAttributes(
      this.fgColor?.rgb,
      this.bgColor?.rgb,
      this.waveColor?.rgb,
      this.style
    )
  }
