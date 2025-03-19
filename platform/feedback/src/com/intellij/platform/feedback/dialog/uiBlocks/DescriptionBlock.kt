// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel

class DescriptionBlock(@NlsContexts.Label private val myLabel: String) : FeedbackBlock, TextDescriptionProvider {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        text(myLabel, maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
      }.bottomGap(BottomGap.SMALL)
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myLabel)
      appendLine()
    }
  }
}