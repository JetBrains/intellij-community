// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

class CheckBoxGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myItemsData: List<CheckBoxItemData>,
  private val myJsonGroupName: String,
) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var requireAnswer = false
  private var myIncludeOtherTextField = false
  private var myOtherProperty: String = ""
  private var myOtherTextfieldPlaceholderText: String = ""

  private var otherCheckBox: JBCheckBox? = null
  private var otherTextField: JBTextField? = null

  override fun addToPanel(panel: Panel) {
    val allCheckBoxes: ArrayList<JBCheckBox> = arrayListOf()

    panel.apply {
      buttonsGroup(indent = false) {
        row {
          label(myGroupLabel).apply {
            bold()
            if (requireAnswer) {
              val errorMessage = if (myIncludeOtherTextField) {
                CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.require.not.empty.with.other")
              }
              else {
                CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.require.not.empty")
              }

              errorOnApply(errorMessage) {
                val isAllCheckboxEmpty = allCheckBoxes.all {
                  !it.isSelected
                }
                if (myIncludeOtherTextField) {
                  return@errorOnApply isAllCheckboxEmpty &&
                                      (otherCheckBox?.isSelected == false ||
                                       (otherCheckBox?.isSelected == true && otherTextField?.text?.isBlank() == true))
                }
                else {
                  return@errorOnApply isAllCheckboxEmpty
                }
              }
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
            cell(JBCheckBox())
              .gap(RightGap.SMALL)
              .applyToComponent {
                isOpaque = false
                otherCheckBox = this
              }
            textField()
              .bindText(::myOtherProperty.toMutableProperty())
              .align(Align.FILL)
              .enabledIf(otherCheckBox!!.selected)
              .applyToComponent {
                emptyText.text = myOtherTextfieldPlaceholderText
                otherTextField = this

                addFocusListener(object : FocusListener {
                  override fun focusGained(e: FocusEvent?) {
                  }

                  override fun focusLost(e: FocusEvent?) {
                    if (e?.oppositeComponent == otherCheckBox) {
                      return
                    }
                    if (text.isBlank()) {
                      otherCheckBox?.setSelected(false)
                    }
                  }
                })
                addMouseListener(object : MouseAdapter() {
                  override fun mouseClicked(e: MouseEvent?) {
                    otherCheckBox?.setSelected(true)
                    requestFocusInWindow()
                  }
                })
              }
            otherCheckBox?.apply {
              addChangeListener(object : ChangeListener {
                override fun stateChanged(e: ChangeEvent?) {
                  val sourceState = e?.source ?: return
                  if (sourceState is JBCheckBox && sourceState.selected()) {
                    otherTextField?.requestFocusInWindow()
                  }
                }
              })
            }
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

      if (myIncludeOtherTextField && otherCheckBox?.isSelected == true && otherTextField?.text?.isBlank() == false) {
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
        if (myIncludeOtherTextField && otherCheckBox?.isSelected == true && otherTextField?.text?.isBlank() == false) {
          put("other", myOtherProperty)
        }
      })
    }
  }

  fun addOtherTextField(
    placeholderText: String = CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.other.placeholder"),
  ): CheckBoxGroupBlock {
    myIncludeOtherTextField = true
    myOtherTextfieldPlaceholderText = placeholderText
    return this
  }

  fun requireAnswer(): CheckBoxGroupBlock {
    requireAnswer = true
    return this
  }
}