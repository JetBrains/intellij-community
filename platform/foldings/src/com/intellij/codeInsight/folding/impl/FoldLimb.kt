// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl

import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.util.registry.Registry
import java.text.BreakIterator


internal data class FoldLimb(
  val startOffset: Int,
  val endOffset: Int,
  val placeholderText: String,
  val groupId: Long?,
  val neverExpands: Boolean,
  val isExpanded: Boolean,
  val isCollapsedByDefault: Boolean,
  val isFrontendCreated: Boolean,
) {

  constructor(foldRegion: FoldRegion) : this(
    foldRegion.startOffset,
    foldRegion.endOffset,
    createPlaceholderText(foldRegion.placeholderText),
    foldRegion.group?.id,
    foldRegion.shouldNeverExpand(),
    foldRegion.isExpanded,
    CodeFoldingManagerImpl.getCollapsedByDefault(foldRegion) == true,
    CodeFoldingManagerImpl.isFrontendCreated(foldRegion),
  )

  override fun toString(): String {
    val groupStr = if (groupId == null) "" else " $groupId,"
    val expandedStr = if (isExpanded) "-" else "+"
    val default = "def:${(if (isCollapsedByDefault) "+" else "-")}"
    val feOrBe = if (isFrontendCreated) "FRONTEND" else "BACKEND"
    return "($startOffset-$endOffset,$groupStr '$placeholderText', $expandedStr, $default, $feOrBe)"
  }

  companion object {
    private const val PLACEHOLDER_SYMBOL = " "

    private fun createPlaceholderText(text: String): String {
      return if (Registry.`is`("cache.folding.model.hide.placeholder")) {
        PLACEHOLDER_SYMBOL.repeat(text.graphemeCount())
      } else {
        text
      }
    }

    private fun String.graphemeCount(): Int {
      val iterator = BreakIterator.getCharacterInstance()
      iterator.setText(this)
      var count = 0
      while (iterator.next() != BreakIterator.DONE) {
        count++
      }
      return count
    }
  }
}
