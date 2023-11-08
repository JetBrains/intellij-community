// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.util.ui.JBFont

class TopLabelBlock(@NlsContexts.Label private val myText: String) : FeedbackBlock, TextDescriptionProvider {

  private var bottomGap: BottomGap? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        label(myText)
          .applyToComponent {
            font = JBFont.h1()
          }
      }.apply {
        if (bottomGap != null) {
          bottomGap(bottomGap!!)
        }
      }
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myText)
      appendLine()
    }
  }

  fun setBottomGap(bottomGap: BottomGap): TopLabelBlock {
    this.bottomGap = bottomGap
    return this
  }
}