// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview

import com.intellij.codeInsight.highlighting.HighlightManagerImpl
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import org.jetbrains.annotations.ApiStatus
import java.awt.Font

@ApiStatus.Internal
class EditorLivePreviewPresentation(private val colorsScheme: EditorColorsScheme) : LivePreviewPresentation {
  override val defaultAttributes: TextAttributes
    get() = colorsScheme.getAttributes(EditorColors.TEXT_SEARCH_RESULT_ATTRIBUTES)

  override val emptyRangeAttributes: TextAttributes
    get() = defaultAttributes.clone().apply {
      effectType = EffectType.BOXED
      effectColor = backgroundColor
    }

  override val excludedAttributes: TextAttributes
    get() = TextAttributes(null, null, colorsScheme.getDefaultForeground(), EffectType.STRIKEOUT, Font.PLAIN)

  override val cursorAttributes: TextAttributes
    get() = TextAttributes(null, null, colorsScheme.getColor(EditorColors.CARET_COLOR),
                           EffectType.ROUNDED_BOX, Font.PLAIN)

  override val selectionAttributes: TextAttributes
    get() = TextAttributes(null, null, JBColor.WHITE, EffectType.ROUNDED_BOX, Font.PLAIN)

  override val defaultLayer: Int = HighlightManagerImpl.OCCURRENCE_LAYER
  override val cursorLayer: Int = HighlightManagerImpl.OCCURRENCE_LAYER
}