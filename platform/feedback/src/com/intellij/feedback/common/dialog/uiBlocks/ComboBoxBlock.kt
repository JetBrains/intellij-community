// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.COMBOBOX_COLUMN_SIZE
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

class ComboBoxBlock(@NlsContexts.Label private val myLabel: String,
                    private val myItems: List<String>,
                    private val myJsonElementName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myProperty: String? = ""
  private var myComment: String? = null
  private var myColumnSize: Int = COMBOBOX_COLUMN_SIZE

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        comboBox(myItems)
          .label(myLabel, LabelPosition.TOP)
          .bindItem(::myProperty.toMutableProperty())
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
      appendLine(myProperty)
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonElementName, myProperty)
    }
  }

  fun addComment(@NlsContexts.Label comment: String): ComboBoxBlock {
    myComment = comment
    return this
  }

  fun setColumnSize(columnSize: Int): ComboBoxBlock {
    myColumnSize = columnSize
    return this
  }
}