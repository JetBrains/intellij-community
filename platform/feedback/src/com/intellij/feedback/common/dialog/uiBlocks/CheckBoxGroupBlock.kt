// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class CheckBoxGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myItemsData: List<CheckBoxItemData>) : BaseFeedbackBlock() {

  private var myOtherProperty: ObservableMutableProperty<String>? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      @Suppress("DialogTitleCapitalization")
      buttonsGroup(myGroupLabel) {
        myItemsData.forEachIndexed { i, itemData ->
          row {
            checkBox(itemData.label).bindSelected(itemData.property)
          }.apply {
            if (i == myItemsData.size - 1 && myOtherProperty == null) {
              this.bottomGap(BottomGap.MEDIUM)
            }
          }
        }

        if (myOtherProperty != null) {
          row {
            textField().applyToComponent {
              emptyText.text = CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.other.placeholder")
            }.bindText(myOtherProperty!!).columns(COLUMNS_MEDIUM)
          }.bottomGap(BottomGap.MEDIUM)
        }
      }
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myGroupLabel)
      myItemsData.forEach { itemData ->
        appendLine(" ${itemData.label} - ${itemData.property.get()}")
      }

      if (myOtherProperty != null) {
        appendLine(" Other: ${myOtherProperty!!.get()}")
      }
      appendLine()
    }
  }

  fun addOtherTextField(otherProperty: ObservableMutableProperty<String>): CheckBoxGroupBlock {
    myOtherProperty = otherProperty
    return this
  }
}