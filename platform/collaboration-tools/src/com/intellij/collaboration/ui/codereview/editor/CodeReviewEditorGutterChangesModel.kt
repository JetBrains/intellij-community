// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor

import com.intellij.openapi.vcs.ex.LineStatusMarkerRangesSource
import com.intellij.openapi.vcs.ex.LstRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

/**
 * A UI model for an editor with gutter changes highlighting
 * This model should exist in the same scope as the editor
 * One model - one editor
 */
interface CodeReviewEditorGutterChangesModel : LineStatusMarkerRangesSource<LstRange> {

  /**
   * Ranges changed in the tracked review
   * These ranges represent changes between file state in review base and the current state of the file (document)
   */
  val reviewRanges: StateFlow<List<LstRange>?>

  override fun getRanges(): List<LstRange>? = reviewRanges.value.takeIf { isValid() }

  override fun findRange(range: LstRange): LstRange? = getRanges()?.find {
    it.vcsLine1 == range.vcsLine1 && it.vcsLine2 == range.vcsLine2 &&
    it.line1 == range.line1 && it.line2 == range.line2
  }
}

@ApiStatus.Internal
class MutableCodeReviewEditorGutterChangesModel : CodeReviewEditorGutterChangesModel {
  private val _reviewRanges = MutableStateFlow<List<LstRange>?>(null)
  override val reviewRanges: StateFlow<List<LstRange>?> = _reviewRanges.asStateFlow()

  override fun isValid(): Boolean = _reviewRanges.value != null

  override fun getRanges(): List<LstRange>? = reviewRanges.value

  override fun findRange(range: LstRange): LstRange? = getRanges()?.find {
    it.vcsLine1 == range.vcsLine1 && it.vcsLine2 == range.vcsLine2 &&
    it.line1 == range.line1 && it.line2 == range.line2
  }

  fun setChanges(changedRanges: List<LstRange>?) {
    _reviewRanges.value = changedRanges
  }
}