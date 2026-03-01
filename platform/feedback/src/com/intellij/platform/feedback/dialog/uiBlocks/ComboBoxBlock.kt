// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.COMBOBOX_COLUMN_SIZE
import com.intellij.platform.feedback.dialog.createBoldJBLabel
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.toMutableProperty
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put

class ComboBoxBlock(@NlsContexts.Label private val myLabel: String,
                    private val myItems: List<String>,
                    private val myJsonElementName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myProperty: String? = ""
  private var myComment: @NlsContexts.DetailedDescription String? = null
  private var myColumnSize: Int = COMBOBOX_COLUMN_SIZE
  private var myRandomizeOptionOrder: Boolean = false
  private var myUseAlignFill: Boolean = false

  override fun addToPanel(panel: Panel) {
    val items = if (myRandomizeOptionOrder) myItems.shuffled() else myItems

    panel.apply {
      row {
        comboBox(items)
          .label(createBoldJBLabel(myLabel), LabelPosition.TOP)
          .bindItem(::myProperty.toMutableProperty())
          .apply {
            if (myUseAlignFill) {
              align(Align.FILL)
            }
            else {
              columns(myColumnSize)
            }
          }
          .applyToComponent {
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

  fun useFillAlign(): ComboBoxBlock {
    myUseAlignFill = true
    return this
  }

  fun randomizeOptionOrder(): ComboBoxBlock {
    myRandomizeOptionOrder = true
    return this
  }
}