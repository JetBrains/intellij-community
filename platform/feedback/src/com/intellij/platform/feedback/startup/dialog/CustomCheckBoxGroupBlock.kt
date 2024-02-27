// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.startup.dialog

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.uiBlocks.CheckBoxItemData
import com.intellij.platform.feedback.dialog.uiBlocks.FeedbackBlock
import com.intellij.platform.feedback.dialog.uiBlocks.JsonDataProvider
import com.intellij.platform.feedback.dialog.uiBlocks.TextDescriptionProvider
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.bindSelected
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CustomCheckBoxGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myPairItemsData: List<Pair<CheckBoxItemData, CheckBoxItemData>>,
  private val myNoneItemData: CheckBoxItemData,
  private val myJsonGroupName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {


  override fun addToPanel(panel: Panel) {
    val allCheckBoxes: ArrayList<JBCheckBox> = arrayListOf()
    lateinit var noneCheckBox: JBCheckBox

    val leftItemData: List<CheckBoxItemData> = myPairItemsData.map { it.first }.shuffled()
    val rightItemData: List<CheckBoxItemData> = myPairItemsData.map { it.second }.shuffled()

    panel.apply {
      buttonsGroup(indent = false) {
        row {
          label(myGroupLabel).apply {
            bold()
            errorOnApply(CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.require.not.empty")) {
              val isAllCheckboxEmpty = allCheckBoxes.all {
                !it.isSelected
              } && !noneCheckBox.isSelected

              return@errorOnApply isAllCheckboxEmpty
            }
          }
        }.bottomGap(BottomGap.NONE)
        row {
          checkBox(myNoneItemData.label).bindSelected(
            { myNoneItemData.property },
            { myNoneItemData.property = it }
          ).applyToComponent {
            noneCheckBox = this
          }.onChanged { noneCB ->
            allCheckBoxes.forEach { it.isEnabled = !noneCB.isSelected }
          }
        }.bottomGap(BottomGap.SMALL)
        row {
          panel {
            leftItemData.forEachIndexed { i, itemDate ->
              row {
                checkBox(itemDate.label).bindSelected(
                  { leftItemData[i].property },
                  { leftItemData[i].property = it }
                ).applyToComponent {
                  allCheckBoxes.add(this)
                }
              }
            }
          }.gap(RightGap.COLUMNS)
          panel {
            rightItemData.forEachIndexed { i, itemData ->
              row {
                checkBox(itemData.label).bindSelected(
                  { rightItemData[i].property },
                  { rightItemData[i].property = it }
                ).applyToComponent {
                  allCheckBoxes.add(this)
                }
              }
            }
          }
        }.bottomGap(BottomGap.MEDIUM)
      }
    }
  }

  override fun collectBlockTextDescription(stringBuilder: StringBuilder) {
    stringBuilder.apply {
      appendLine(myGroupLabel)
      appendLine(" ${myNoneItemData.label} - ${myNoneItemData.property}")
      myPairItemsData.flatMap { it.toList() }.forEach { itemData ->
        appendLine(" ${itemData.label} - ${if (myNoneItemData.property) "false" else itemData.property}")
      }
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonGroupName, buildJsonObject {
        put(myNoneItemData.jsonElementName, myNoneItemData.property)
        myPairItemsData.flatMap { it.toList() }.forEach { itemData ->
          put(itemData.jsonElementName, if (myNoneItemData.property) "false" else itemData.property.toString())
        }
      })
    }
  }

}