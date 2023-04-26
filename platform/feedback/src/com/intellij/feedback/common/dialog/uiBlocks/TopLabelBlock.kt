// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBFont

class TopLabelBlock(@NlsContexts.Label val myText: String) : BaseFeedbackBlock() {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        label(myText)
          .applyToComponent {
            font = JBFont.h1()
          }
      }
    }
  }
}