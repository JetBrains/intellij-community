// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find.backend

import com.intellij.find.FindModel
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.platform.find.FindInProjectModel
import com.intellij.platform.find.FindInProjectResult
import com.intellij.platform.find.FindRemoteApi
import com.intellij.platform.find.RdFindInProjectNavigation
import com.intellij.platform.find.RdSimpleTextAttributes
import com.intellij.platform.find.RdTextChunk
import com.intellij.platform.project.findProject
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NotNull
import java.awt.Font

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
        searchContext = FindModel.SearchContext.valueOf(model.searchContext)
      }
      val project = model.projectId.findProject()
      //TODO rewrite find function without using progress indicator and presentation
      val progressIndicator = EmptyProgressIndicator()
      val presentation = FindUsagesProcessPresentation(UsageViewPresentation())

      coroutineScope {

        FindInProjectUtil.findUsages(findModel, project, progressIndicator, presentation, emptySet()) { usageInfo ->
          val virtualFile = usageInfo.virtualFile
          if (virtualFile == null)
            return@findUsages true

          val adapter = UsageInfo2UsageAdapter(usageInfo).also { it.updateCachedPresentation() }
          val textChunks = adapter.text.drop(1).map {
            val attributes = createSimpleTextAttributes(it)
            RdTextChunk(it.text, attributes)
          }

          val findResultItem = FindInProjectResult(
            textChunks,
            adapter.line + 1,
            usageInfo.navigationOffset,
            usageInfo.rangeInElement?.length ?: 0,
            virtualFile.rpcId(),
            virtualFile.path
          )
          launch {
            send(findResultItem)
          }
          true
          //val result = trySend(findResultItem)
          //when {
          //  result.isSuccess -> true  // Continue processing
          //  result.isClosed || result.isFailure -> {
          //    progressIndicator.cancel() // Cancel the search process
          //    result.exceptionOrNull()?.let { throw it }
          //    false  // Stop processing on failure
          //  }
          //  else -> true  // Continue in other cases
          //}
        }
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  override suspend fun navigate(request: RdFindInProjectNavigation) {
    TODO("Not yet implemented")
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
