// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBUI
import javax.swing.SwingConstants

class SegmentedButtonBlock<T>(myProperty: ObservableMutableProperty<T>,
                              @NlsContexts.Label val mainLabel: String?,
                              val items: List<T>,
                              val renderer: (T) -> String,
                              @NlsContexts.Label val leftBottomLabel: String? = null,
                              @NlsContexts.Label val midBottomLabel: String? = null,
                              @NlsContexts.Label val rightBottomLabel: String? = null) : SingleInputFeedbackBlock<T>(myProperty) {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      if (mainLabel != null) {
        row {
          label(mainLabel)
            .customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
        }.bottomGap(BottomGap.SMALL)
      }
      row {
        segmentedButton(items, renderer)
          .apply {
            maxButtonsCount(items.size)
          }.customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .whenItemSelected { myProperty.set(it) }
          .align(Align.FILL)
          .validation {
            addApplyRule(CommonFeedbackBundle.message("dialog.feedback.segmentedButton.required")) { it.selectedItem == null }
          }
      }.apply {
        if (leftBottomLabel == null && midBottomLabel == null && rightBottomLabel == null) {
          this.bottomGap(BottomGap.MEDIUM)
          return
        }
      }

      row {
        if (leftBottomLabel != null) {
          label(leftBottomLabel)
            .applyToComponent {
              font = ComponentPanelBuilder.getCommentFont(font)
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
            .widthGroup("Group")
            .apply {
              if (midBottomLabel == null) {
                resizableColumn()
              }
            }
        }
        if (midBottomLabel != null) {
          label(midBottomLabel)
            .applyToComponent {
              font = ComponentPanelBuilder.getCommentFont(font)
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
            }
            .align(AlignX.CENTER)
            .resizableColumn()
        }
        if (rightBottomLabel != null) {
          label(rightBottomLabel)
            .applyToComponent {
              font = ComponentPanelBuilder.getCommentFont(font)
              foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
              horizontalAlignment = SwingConstants.RIGHT
            }
            .widthGroup("Group")
        }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }
}