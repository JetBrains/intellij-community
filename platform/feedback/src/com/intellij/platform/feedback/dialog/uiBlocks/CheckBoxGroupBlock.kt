// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CheckBoxGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myItemsData: List<CheckBoxItemData>,
  private val myJsonGroupName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var requireAnswer = false
  private var myIncludeOtherTextField = false
  private var myOtherProperty: String = ""

  override fun addToPanel(panel: Panel) {
    val allCheckBoxes: ArrayList<JBCheckBox> = arrayListOf()
    var otherTextField: JBTextField? = null

    panel.apply {
      buttonsGroup(indent = false) {
        row {
          label(myGroupLabel).bold().errorOnApply(CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.require.not.empty")) {
            val isAllCheckboxEmpty = allCheckBoxes.all {
              !it.isSelected
            }
            if (myIncludeOtherTextField) {
              return@errorOnApply isAllCheckboxEmpty && otherTextField?.text?.isBlank() ?: false
            }
            else {
              return@errorOnApply isAllCheckboxEmpty
            }
          }
        }.bottomGap(BottomGap.NONE)
        myItemsData.forEachIndexed { i, itemData ->
          row {
            checkBox(itemData.label).bindSelected(
              { myItemsData[i].property },
              { myItemsData[i].property = it }
            ).applyToComponent {
              allCheckBoxes.add(this)
            }
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
              .applyToComponent { otherTextField = this }
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

  fun requireAnswer(): CheckBoxGroupBlock {
    requireAnswer = true
    return this
  }
}