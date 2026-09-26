// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.util.ui.JBUI
import javax.swing.Icon
import javax.swing.border.Border

/**
 * Adds the row with the image to the feedback form.
 *
 * Use [com.intellij.openapi.util.IconLoader.getIcon] to create the [Icon] instance.
 */
class ImageBlock(private val icon: Icon) : FeedbackBlock {
  private var border: Border = JBUI.Borders.empty()

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        icon(icon)
          .align(AlignX.CENTER)
          .gap(RightGap.SMALL)
          .applyToComponent {
            border = this@ImageBlock.border
          }
      }.bottomGap(BottomGap.SMALL)
    }
  }

  fun withBorder(border: Border): ImageBlock {
    this.border = border
    return this
  }
}