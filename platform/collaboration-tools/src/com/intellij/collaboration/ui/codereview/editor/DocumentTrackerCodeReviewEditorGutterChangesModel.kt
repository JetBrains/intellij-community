// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.collectScoped
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.diff.util.Range
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vcs.ex.DocumentTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LstRange
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Changes model which uses [DocumentTracker] to track and shift changes between review head and current document state
 */
@ApiStatus.Internal
@ApiStatus.ScheduledForRemoval
@Deprecated("Please use com.intellij.collaboration.ui.codereview.editor.ReviewInEditorUtil.trackDocumentDiffSync")
class DocumentTrackerCodeReviewEditorGutterChangesModel(
  parentCs: CoroutineScope,
  private val document: Document,
  reviewHeadContent: Flow<CharSequence?>,
  reviewChangesRanges: Flow<List<Range>?>
) : CodeReviewEditorGutterChangesModel {
  private val cs = parentCs.childScope(javaClass.name, Dispatchers.Main)

  private val _reviewRanges = MutableStateFlow<List<LstRange>?>(null)
  override val reviewRanges: StateFlow<List<LstRange>?> = _reviewRanges.asStateFlow()

  private val _postReviewRanges = MutableStateFlow<List<Range>?>(null)

  /**
   * Ranges changed between review head and current document content
   */
  val postReviewRanges: StateFlow<List<Range>?> = _postReviewRanges.asStateFlow()

  override fun isValid(): Boolean = cs.isActive && _reviewRanges.value != null

  init {
    cs.launchNow {
      combine(reviewHeadContent, reviewChangesRanges, ::Pair).collectScoped { (headContent, reviewRanges) ->
        if (headContent != null && reviewRanges != null) {
          trackChanges(headContent, reviewRanges)
        }
      }
    }
  }

  private suspend fun trackChanges(originalContent: CharSequence, reviewRanges: List<Range>) {
    withContext(Dispatchers.Main.immediate) {
      val reviewHeadDocument = LineStatusTrackerBase.createVcsDocument(originalContent)
      ReviewInEditorUtil.trackDocumentDiffSync(reviewHeadDocument, document) { trackerRanges ->
        _postReviewRanges.value = trackerRanges
        _reviewRanges.value = ExcludingApproximateChangedRangesShifter.shift(reviewRanges, trackerRanges).map(Range::asLst)
      }
    }
  }
}