package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.ui.validation.WHEN_STATE_CHANGED
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
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

class RadioButtonGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myItemsData: List<RadioButtonItemData>,
  private val myJsonGroupName: String,
) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  private var myIncludeOtherTextField = false
  private var otherProperty: String = ""
  private var otherTextfieldPlaceholderText: String = ""

  private var requireAnswer = false
  private var otherRadioButton: JBRadioButton? = null
  private var otherTextField: JBTextField? = null

  override fun addToPanel(panel: Panel) {
    val allButtons: ArrayList<JBRadioButton> = arrayListOf()

    panel.apply {
      buttonsGroup(indent = false) {
        row {
          val errorMessage = if (myIncludeOtherTextField) {
            CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.require.not.empty.with.other")
          }
          else {
            CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.require.not.empty")
          }
          label(myGroupLabel)
            .bold()
            .errorOnApply(errorMessage) {
              if (myIncludeOtherTextField) {
                allButtons.all { !it.isSelected } && (otherRadioButton?.isSelected == false ||
                                                      (otherRadioButton?.isSelected == true && otherTextField?.text?.isBlank() == true))
              }
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
            if (i == myItemsData.size - 1 && !myIncludeOtherTextField) {
              this.bottomGap(BottomGap.MEDIUM)
            }
          }
        }

        if (myIncludeOtherTextField) {
          row {
            cell(JBRadioButton())
              .applyToComponent {
                isOpaque = false
                otherRadioButton = this
              }
            textField()
              .bindText(::otherProperty.toMutableProperty())
              .align(Align.FILL)
              .enabledIf(otherRadioButton!!.selected)
              .applyToComponent {
                emptyText.text = otherTextfieldPlaceholderText
                otherTextField = this

                addFocusListener(object : FocusListener {
                  override fun focusGained(e: FocusEvent?) {
                  }

                  override fun focusLost(e: FocusEvent?) {
                    if (e?.oppositeComponent == otherRadioButton) {
                      return
                    }
                    if (text.isBlank()) {
                      otherRadioButton?.setSelected(false)
                    }
                  }
                })
                addMouseListener(object : MouseAdapter() {
                  override fun mouseClicked(e: MouseEvent?) {
                    otherRadioButton?.setSelected(true)
                    requestFocusInWindow()
                  }
                })
              }
            otherRadioButton?.apply {
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
      appendLine()
      if (myIncludeOtherTextField && otherRadioButton?.isSelected == true && otherTextField?.text?.isBlank() == false) {
        appendLine(" Other: ${otherProperty}")
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
        if (myIncludeOtherTextField && otherRadioButton?.isSelected == true && otherTextField?.text?.isBlank() == false) {
          put("other", otherProperty)
        }
      })
    }
  }

  fun addOtherTextField(
    placeholderText: String = CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.other.placeholder"),
  ): RadioButtonGroupBlock {
    myIncludeOtherTextField = true
    otherTextfieldPlaceholderText = placeholderText
    return this
  }

  fun requireAnswer(): RadioButtonGroupBlock {
    requireAnswer = true
    return this
  }
}

data class RadioButtonItemData(
  @NlsContexts.RadioButton val label: String,
  val jsonElementName: String,
) {
  internal var property: Boolean = false
}