// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import javax.swing.Icon

/**
 * Adds the row with the image to the feedback form.
 *
 * Use [com.intellij.openapi.util.IconLoader.getIcon] to create the [Icon] instance.
 */
class ImageBlock(private val icon: Icon) : FeedbackBlock {
  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        icon(icon)
          .align(AlignX.CENTER)
          .gap(RightGap.SMALL)
      }.bottomGap(BottomGap.SMALL)
    }
  }
}