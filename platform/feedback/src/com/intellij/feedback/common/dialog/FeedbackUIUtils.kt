// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.ui.panel.ComponentPanelBuilder
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.util.ui.JBUI
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.SwingConstants


fun JBTextArea.adjustBehaviourForFeedbackForm() {
  wrapStyleWord = true
  lineWrap = true
  addKeyListener(object : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      if (e.keyCode == KeyEvent.VK_TAB) {
        if ((e.modifiersEx and KeyEvent.SHIFT_DOWN_MASK) != 0) {
          transferFocusBackward()
        }
        else {
          transferFocus()
        }
        e.consume()
      }
    }
  })
}

const val TEXT_AREA_ROW_SIZE = 5
const val TEXT_AREA_COLUMN_SIZE = 42
const val TEXT_FIELD_EMAIL_COLUMN_SIZE = 25

val EMAIL_REGEX = Regex(".+@.+\\..+")

fun <T> Panel.createSegmentedButtonWithBottomLabels(@NlsContexts.Label mainLabel: String?, items: List<T>, renderer: (T) -> String,
                                                    size: Int, bindProperty: ObservableMutableProperty<T>,
                                                    @NlsContexts.Label leftBottomLabel: String?, @NlsContexts.Label midBottomLabel: String?,
                                                    @NlsContexts.Label rightBottomLabel: String?) {
  panel {
    if (mainLabel != null) {
      row {
        label(mainLabel)
          .customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
          .bold()
      }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
    }
    row {
      segmentedButton(items, renderer)
        .apply {
          maxButtonsCount(size)
        }.customize(Gaps(top = IntelliJSpacingConfiguration().verticalComponentGap))
        .whenItemSelected { bindProperty.set(it) }
        .align(Align.FILL)
        .validation {
          addApplyRule(CommonFeedbackBundle.message("dialog.feedback.segmentedButton.error")) { it.selectedItem == null }
        }
    }

    if (leftBottomLabel == null && midBottomLabel == null && rightBottomLabel == null) {
      return@panel
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
    }
  }
}