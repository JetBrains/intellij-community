// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.impl.zombie.LimbedZombie
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*
import javax.swing.Icon

@Internal
class HighlightingZombie(highlighters: List<HighlightingLimb>) : LimbedZombie<HighlightingLimb>(highlighters)

@Internal
data class HighlightingLimb(
  val startOffset: Int,
  val endOffset: Int,
  val layer: Int,
  val targetArea: HighlighterTargetArea,
  val textAttributesKey: TextAttributesKey?,
  val textAttributes: TextAttributes?,
  val gutterIcon: Icon?,
) {
  constructor(highlighter: RangeHighlighter, highlighterLayer: Int, colorsScheme: EditorColorsScheme) : this(
    startOffset = highlighter.startOffset,
    endOffset = highlighter.endOffset,
    layer = highlighterLayer, // because Rider needs to modify its zombie's layers
    targetArea = highlighter.targetArea,
    textAttributesKey = highlighter.textAttributesKey,
    textAttributes = highlighter.getTextAttributes(colorsScheme),
    gutterIcon = highlighter.gutterIconRenderer?.icon,
  )

  override fun equals(other: Any?): Boolean {
    // exclude gutterIcon
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val state = other as HighlightingLimb
    return startOffset == state.startOffset &&
           endOffset == state.endOffset &&
           layer == state.layer &&
           targetArea == state.targetArea &&
           textAttributesKey == state.textAttributesKey &&
           textAttributes == state.textAttributes
  }

  override fun hashCode(): Int {
    // exclude gutterIcon
    return Objects.hash(startOffset, endOffset, layer, targetArea, textAttributesKey, textAttributes)
  }
}
