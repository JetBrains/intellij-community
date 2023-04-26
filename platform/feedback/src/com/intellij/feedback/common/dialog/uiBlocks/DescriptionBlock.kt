// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.openapi.ui.ex.MultiLineLabel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel

class DescriptionBlock(@NlsContexts.Label val label: String) : BaseFeedbackBlock() {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        cell(MultiLineLabel(label))
      }.bottomGap(BottomGap.SMALL)
    }
  }
}