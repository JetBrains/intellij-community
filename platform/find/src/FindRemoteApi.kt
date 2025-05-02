// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usages.TextChunk
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color

@ApiStatus.Internal
@Rpc
interface FindRemoteApi : RemoteApi<Unit> {

  suspend fun findByModel(model: FindInProjectModel): Flow<FindInFilesResult>

  companion object {
    @JvmStatic
    suspend fun getInstance(): FindRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<FindRemoteApi>())
    }
  }
}

@Serializable
data class FindInProjectModel (
  val projectId: ProjectId,
  val stringToFind: String,
  val isWholeWordsOnly: Boolean,
  val isRegularExpressions: Boolean,
  val isCaseSensitive: Boolean,
  val isMultiline: Boolean,
  val isPreserveCase: Boolean,
  val isProjectScope: Boolean,
  val isCustomScope: Boolean,
  val isMultipleFiles: Boolean,
  val isReplaceState: Boolean,
  val isPromptOnReplace: Boolean,
  val fileFilter: String?,
  val moduleName: String?,
  val directoryName: String?,
  val isWithSubdirectories: Boolean,
  val searchContext: String,
  val scopeId: Int?,
  val filesToScanInitially: List<VirtualFileId>
)

@Serializable
data class FindInFilesResult(
  val presentation: List<RdTextChunk>,
  val line: Int,
  val offset: Int,
  val originalOffset: Int,
  val length: Int,
  val originalLength: Int,
  val fileId: VirtualFileId,
  val path: String,
  @param:NlsSafe val presentablePath: String,
  val merged: Boolean,
  val backgroundColor: ColorId?,
  val usagesCount: Int,
  val fileCount: Int,
)

@Serializable
data class RdTextChunk (
  val text: @Nls String,
  val attributes: RdSimpleTextAttributes
) {
  fun toTextChunk(): TextChunk {
    val textAttributes = attributes.toInstance().toTextAttributes()
    if (textAttributes.effectType == EffectType.SEARCH_MATCH) {
      textAttributes.fontType = SimpleTextAttributes.STYLE_BOLD
    }
    return TextChunk(textAttributes, text)
  }
}

@Serializable
data class RdSimpleTextAttributes (
  val fgColor: Int? = null,
  val bgColor: Int? = null,
  val waveColor: Int? = null,
  val style: Int = 0
) {
  fun toInstance(): SimpleTextAttributes {
    return SimpleTextAttributes(
      this.bgColor?.let { Color(it) },
      this.fgColor?.let { Color(it) },
      this.waveColor?.let { Color(it) },
      this.style
    )
  }
}

