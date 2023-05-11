// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.dialog.TEXT_AREA_COLUMN_SIZE
import com.intellij.feedback.common.dialog.TEXT_AREA_ROW_SIZE
import com.intellij.feedback.common.dialog.adjustBehaviourForFeedbackForm
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class TextAreaBlock(private val myProperty: ObservableMutableProperty<String>,
                    @NlsContexts.Label private val myLabel: String) : BaseFeedbackBlock() {

  private var myTextAreaRowSize: Int = TEXT_AREA_ROW_SIZE
  private var myTextAreaColumnSize: Int = TEXT_AREA_COLUMN_SIZE
  private var myRequireNotEmptyMessage: String? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        textArea()
          .bindText(myProperty)
          .rows(myTextAreaRowSize)
          .columns(myTextAreaColumnSize)
          .label(myLabel, LabelPosition.TOP)
          .applyToComponent {
            adjustBehaviourForFeedbackForm()
          }
          .apply {
            if (myRequireNotEmptyMessage != null) {
              errorOnApply(myRequireNotEmptyMessage!!) {
                myProperty.get().isBlank()
              }
            }
          }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myLabel)
      appendLine(myProperty.get())
      appendLine()
    }
  }

  fun setRowSize(rowSize: Int): TextAreaBlock {
    myTextAreaRowSize = rowSize
    return this
  }

  fun setColumnSize(columnSize: Int): TextAreaBlock {
    myTextAreaColumnSize = columnSize
    return this
  }

  fun requireNotEmpty(requireNotEmptyMessage: String): TextAreaBlock {
    myRequireNotEmptyMessage = requireNotEmptyMessage
    return this
  }
}