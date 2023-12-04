package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.ui.validation.WHEN_STATE_CHANGED
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.dsl.builder.*
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RadioButtonGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myItemsData: List<RadioButtonItemData>,
  private val myJsonGroupName: String) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var requireAnswer = false

  override fun addToPanel(panel: Panel) {
    val allButtons: ArrayList<JBRadioButton> = arrayListOf()

    panel.apply {
      buttonsGroup(indent = false) {
        row {
          label(myGroupLabel)
            .bold()
            .errorOnApply(CommonFeedbackBundle.message("dialog.feedback.radioGroup.require.not.empty")) {
              return@errorOnApply requireAnswer && allButtons.all { !it.isSelected }
            }.apply {
              validationRequestor { parentDisposable, validate ->
                allButtons.forEach {
                  WHEN_STATE_CHANGED.invoke(it).subscribe(parentDisposable, validate)
                }
              }
            }
        }.bottomGap(BottomGap.NONE)
        myItemsData.forEachIndexed { i, itemData ->
          row {
            radioButton(itemData.label).bindSelected(
              { myItemsData[i].property },
              { myItemsData[i].property = it }
            ).applyToComponent {
              allButtons.add(this)
            }
          }.apply {
            if (i == myItemsData.size - 1) {
              this.bottomGap(BottomGap.MEDIUM)
            }
          }
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
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonGroupName, buildJsonObject {
        myItemsData.forEach { itemData ->
          put(itemData.jsonElementName, itemData.property)
        }
      })
    }
  }

  fun requireAnswer(): RadioButtonGroupBlock {
    requireAnswer = true
    return this
  }
}

data class RadioButtonItemData(@NlsContexts.RadioButton val label: String,
                               val jsonElementName: String) {
  internal var property: Boolean = false
}