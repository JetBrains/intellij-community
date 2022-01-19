// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.HintWidthAdjustment
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes


class VisualFormattingLayerInlayRenderer(text: String) : HintRenderer(text) {

  override fun getTextAttributes(editor: Editor): TextAttributes? =
    editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.STRING)

  override fun calcWidthInPixels(inlay: Inlay<*>) =
    calcWidthInPixels(inlay.editor, text, HintWidthAdjustment(text!!, null, 0))

}
