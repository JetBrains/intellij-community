// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.identifiers.highlighting.shared

import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.util.TextRange
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * Service for obtaining ranges and attributes for [com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass], which is calling this service on every caret move.
 * It's up to the implementation if it should cache the result, or try to compute if possible (e.g. via [com.intellij.codeInsight.daemon.impl.IdentifierHighlighterPass.computeRanges])
 */
@ApiStatus.Internal
@Rpc
interface IdentifierHighlightingRemoteApi : RemoteApi<Unit> {
  @RequiresBackgroundThread
  @RequiresReadLock
  suspend fun getMarkupData(editorId: EditorId, visibleRange: SerializableRange, offset: Int): IdentifierHighlightingResultRemote

  @Serializable
  data class IdentifierHighlightingResultRemote(
    val occurrences: Collection<IdentifierOccurrenceRemote>,
    /**
     targets are the ranges that the caret should react to,
     e.g. if the entire "return xxx;" is highlighted when the caret is on the "return" keyword, the former range is occurrence, and the latter is the target
     This list is used when the caret is moving - if the caret is inside one of the targets, we need to highlight all the occurrences for these targets
    */
    val targets: Collection<SerializableRange>
  ) {
    companion object {
      val EMPTY:IdentifierHighlightingResultRemote = IdentifierHighlightingResultRemote(listOf(), listOf())
    }
  }

  @Serializable
  data class IdentifierOccurrenceRemote(val range: SerializableRange, val highlightInfoType: HighlightInfoTypeModel)

  companion object {
    @JvmStatic
    suspend fun getInstance(): IdentifierHighlightingRemoteApi {
      return RemoteApiProviderService.Companion.resolve(remoteApiDescriptor<IdentifierHighlightingRemoteApi>())
    }
  }
  @Serializable
  data class SerializableRange(val start: Int, val end: Int) {
    val textRange: TextRange get() = TextRange(start, end)
    constructor(textRange: TextRange) : this(textRange.startOffset, textRange.endOffset)
  }

  @Serializable
  data class HighlightInfoTypeModel (
      val severity: String,
      val attributeKey: String,
      val needsUpdateOnTyping: Boolean
  )
}