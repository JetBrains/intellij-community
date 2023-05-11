// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.COMBOBOX_COLUMN_SIZE
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class ComboBoxBlock(private val myProperty: ObservableMutableProperty<String>,
                    @NlsContexts.Label private val myLabel: String,
                    private val myItems: List<String>) : BaseFeedbackBlock() {

  private var myComment: String? = null
  private var myColumnSize: Int = COMBOBOX_COLUMN_SIZE

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        comboBox(myItems)
          .label(myLabel, LabelPosition.TOP)
          .bindItem(myProperty)
          .columns(myColumnSize).applyToComponent {
            selectedItem = null
          }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.combobox.required")) {
            it.selectedItem == null
          }
        if (myComment != null) {
          comment(myComment!!)
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

  fun addComment(comment: String): ComboBoxBlock {
    myComment = comment
    return this
  }

  fun setColumnSize(columnSize: Int): ComboBoxBlock {
    myColumnSize = columnSize
    return this
  }
}