// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.UnfairTextRange
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Identifier highlighting model main entry point: [getMarkupData]
 * Obtains the identifier highlighting (from cached ranges, or by calling [IdentifierHighlightingAccessor]).
 */
@ApiStatus.Internal
interface IdentifierHighlightingManager {
  @RequiresBackgroundThread
  suspend fun getMarkupData(editor: Editor, visibleRange: ProperTextRange): IdentifierHighlightingResult

  companion object {
    fun getInstance(project: Project): IdentifierHighlightingManager {
      return project.getService(IdentifierHighlightingManager::class.java)
    }
  }
}

@ApiStatus.Internal
@JvmRecord
data class IdentifierOccurrence(val range: Segment, val highlightInfoType: HighlightInfoType)

@ApiStatus.Internal
@JvmRecord
data class IdentifierHighlightingResult(
  val occurrences: Collection<IdentifierOccurrence>,
  /**
  targets are the ranges that the caret should react to,
  e.g. if the entire "return xxx;" is highlighted when the caret is on the "return" keyword, the former range is occurrence, and the latter is the target
  This list is used when the caret is moving - if the caret is inside one of the targets, we need to highlight all the occurrences for these targets
   */
  val targets: Collection<Segment>
) {
  companion object {
    /**
     * This result means that the server is unable to handle request,
     * either because the identifier highlighting is disabled or the caret is not there or the inline prompt is shown etc
     */
    @ApiStatus.Internal
    val EMPTY_RESULT: IdentifierHighlightingResult = IdentifierHighlightingResult(listOf(), listOf())

    /**
     * This result means that the server has a [com.intellij.openapi.editor.Document] version mismatched with the client version, so all queries will likely be incorrect
     * The client must decide what to do in this situation, with the most probable reaction would be to restart the query later
     */
    @ApiStatus.Internal
    val WRONG_DOCUMENT_VERSION: IdentifierHighlightingResult = IdentifierHighlightingResult(listOf(IdentifierOccurrence(UnfairTextRange(-1, -1), HighlightInfoType.DEPRECATED)), listOf())
  }
}