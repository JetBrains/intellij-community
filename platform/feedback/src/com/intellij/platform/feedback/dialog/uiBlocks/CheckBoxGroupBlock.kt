// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.dsl.builder.*
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CheckBoxGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myItemsData: List<CheckBoxItemData>,
  private val myJsonGroupName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myIncludeOtherTextField = false
  private var myOtherProperty: String = ""

  override fun addToPanel(panel: Panel) {
    panel.apply {
      @Suppress("DialogTitleCapitalization")
      buttonsGroup(myGroupLabel) {
        myItemsData.forEachIndexed { i, itemData ->
          row {
            checkBox(itemData.label).bindSelected(
              { myItemsData[i].property },
              { myItemsData[i].property = it }
            )
          }.apply {
            if (i == myItemsData.size - 1 && !myIncludeOtherTextField) {
              this.bottomGap(BottomGap.MEDIUM)
            }
          }
        }

        if (myIncludeOtherTextField) {
          row {
            textField().applyToComponent {
              emptyText.text = CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.other.placeholder")
            }.bindText(::myOtherProperty.toMutableProperty())
              .columns(COLUMNS_MEDIUM)
          }.bottomGap(BottomGap.MEDIUM)
        }
      }
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myGroupLabel)
      myItemsData.forEach { itemData ->
        appendLine(" ${itemData.label} - ${itemData.property}")
      }

      if (myIncludeOtherTextField) {
        appendLine(" Other: ${myOtherProperty}")
      }
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonGroupName, buildJsonObject {
        myItemsData.forEach { itemData ->
          put(itemData.jsonElementName, itemData.property)
        }
        if (myIncludeOtherTextField) {
          put("other", myOtherProperty)
        }
      })
    }
  }

  fun addOtherTextField(): CheckBoxGroupBlock {
    myIncludeOtherTextField = true
    return this
  }
}