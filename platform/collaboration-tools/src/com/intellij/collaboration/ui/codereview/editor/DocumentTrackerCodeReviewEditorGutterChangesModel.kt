// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.collaboration.async.classAsCoroutineName
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.async.nestedDisposable
import com.intellij.collaboration.util.ExcludingApproximateChangedRangesShifter
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.DocumentTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerBase
import com.intellij.openapi.vcs.ex.LstRange
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.ApiStatus

/**
 * Changes model which uses [DocumentTracker] to track and shift changes between review head and current document state
 */
@ApiStatus.Internal
class DocumentTrackerCodeReviewEditorGutterChangesModel(
  parentCs: CoroutineScope,
  document: Document,
  reviewHeadContent: Flow<CharSequence?>,
  reviewChangesRanges: Flow<List<Range>?>
) : CodeReviewEditorGutterChangesModel {
  private val cs = parentCs.childScope(classAsCoroutineName() + Dispatchers.Main)

  private val reviewHeadDocument = LineStatusTrackerBase.createVcsDocument(document)
  private val documentTracker = DocumentTracker(reviewHeadDocument, document).also {
    Disposer.register(cs.nestedDisposable(), it)
  }
  private val shiftedReviewRanges = MutableStateFlow<List<LstRange>?>(null)
  override val reviewRanges: StateFlow<List<LstRange>?> = shiftedReviewRanges.asStateFlow()

  private var initialized = false

  /**
   * Ranges changed between review head and current document content
   */
  val postReviewRanges = MutableStateFlow<List<Range>?>(null)

  override fun isValid(): Boolean = cs.isActive && initialized

  init {
    cs.launchNow(Dispatchers.Main.immediate) {
      reviewHeadContent.collectLatest { content ->
        if (content == null) {
          initialized = false
        }
        else {
          setReviewHeadContent(content)
          reviewChangesRanges.collectLatest { reviewRanges ->
            if (reviewRanges == null) {
              initialized = false
            }
            else {
              val handler = MyTrackerHandler(reviewRanges)
              try {
                initialized = true
                documentTracker.addHandler(handler)
                handler.updateTrackerRanges(reviewRanges)
                awaitCancellation()
              }
              finally {
                documentTracker.removeHandler(handler)
              }
            }
          }
        }
      }
    }
  }

  private fun setReviewHeadContent(content: CharSequence) {
    documentTracker.doFrozen(Side.LEFT) {
      reviewHeadDocument.setReadOnly(false)
      try {
        CommandProcessor.getInstance().runUndoTransparentAction {
          reviewHeadDocument.setText(content)
        }
      }
      finally {
        reviewHeadDocument.setReadOnly(true)
      }
    }
  }

  private inner class MyTrackerHandler(private val reviewRanges: List<Range>) : DocumentTracker.Handler {
    override fun afterBulkRangeChange(isDirty: Boolean) {
      updateTrackerRanges(reviewRanges)
    }

    fun updateTrackerRanges(reviewRanges: List<Range>) {
      val trackerRanges = documentTracker.blocks.map { it.range }
      postReviewRanges.value = trackerRanges
      shiftedReviewRanges.value = ExcludingApproximateChangedRangesShifter.shift(reviewRanges, trackerRanges).map(Range::asLst)
    }
  }
}

private fun Range.asLst(): LstRange = LstRange(start2, end2, start1, end1)