// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
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
  private var myColumnCount: Int = 1

  private var otherCheckBox: JBCheckBox? = null
  private var otherTextField: JBTextField? = null

  override fun addToPanel(panel: Panel) {
    val allCheckBoxes = mutableListOf<JBCheckBox>()

    fun Row.addCheckBox(itemData: CheckBoxItemData) {
      allCheckBoxes += checkBox(itemData.label)
        .bindSelected({ itemData.property }, { itemData.property = it })
        .addChooseOptionValidation(allCheckBoxes)
        .component
    }

    val itemRows = myItemsData.chunked(myColumnCount)

    val groupContent: (Panel) -> Unit = { container ->
      container.buttonsGroup(indent = false) {
        row {
          label(myGroupLabel)
            .bold()
        }.bottomGap(BottomGap.NONE)
        itemRows.forEachIndexed { rowIndex, rowItems ->
          // Use the standard multi-column DSL helpers so the checkboxes align in evenly spaced columns.
          val cells: List<(Row.() -> Unit)?> = rowItems.map { itemData -> { addCheckBox(itemData) } }
          val addedRow = when (myColumnCount) {
            2 -> twoColumnsRow(cells.getOrNull(0), cells.getOrNull(1))
            3 -> threeColumnsRow(cells.getOrNull(0), cells.getOrNull(1), cells.getOrNull(2))
            else -> row { rowItems.forEach { addCheckBox(it) } }
          }
          if (rowIndex == itemRows.lastIndex && !myIncludeOtherTextField) {
            addedRow.bottomGap(BottomGap.MEDIUM)
          }
        }

        if (myIncludeOtherTextField) {
          row {
            otherCheckBox = checkBox("")
              .gap(RightGap.SMALL)
              .addChooseOptionValidation(allCheckBoxes)
              .applyToComponent {
                addChangeListener(object : ChangeListener {
                  override fun stateChanged(e: ChangeEvent?) {
                    val sourceState = e?.source ?: return
                    if (sourceState is JBCheckBox && sourceState.selected()) {
                      otherTextField?.requestFocusInWindow()
                    }
                  }
                })
              }
              .component

            otherTextField = textField()
              .bindText(::myOtherProperty)
              .align(Align.FILL)
              .enabledIf(otherCheckBox!!.selected)
              .applyToComponent {
                emptyText.text = myOtherTextfieldPlaceholderText

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
              }.component
          }.bottomGap(BottomGap.MEDIUM)
        }
      }
    }

    panel.apply {
      if (myColumnCount > 1) {
        // Isolate the multi-column grid in its own sub-panel so its wide columns do not force sibling
        // blocks (e.g. rating rows) to align with them.
        panel { groupContent(this) }
      }
      else {
        groupContent(this)
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

  /**
   * Lays the checkboxes out over the given number of [columnCount] columns (1, 2 or 3) to keep tall option
   * lists compact, using the standard [Panel.twoColumnsRow]/[Panel.threeColumnsRow] layout.
   *
   * Follow the IntelliJ UI guidelines when choosing the value:
   * arrange checkboxes with labels of up to 30 characters in 2 columns,
   * and checkboxes with labels of up to 15 characters in 3 columns.
   *
   * @see <a href="https://plugins.jetbrains.com/docs/intellij/layout.html#checkboxes-and-radio-buttons">Layout guidelines</a>
   */
  fun setColumnCount(columnCount: Int): CheckBoxGroupBlock {
    require(columnCount in 1..3) { "columnCount must be 1, 2 or 3, but was $columnCount" }
    myColumnCount = columnCount
    return this
  }

  private fun <T : JBCheckBox> Cell<T>.addChooseOptionValidation(allCheckBoxes: List<JBCheckBox>): Cell<T> {
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

    component.addActionListener {
      val checkBoxes = (allCheckBoxes + otherCheckBox - component).filterNotNull()
      for (checkBox in checkBoxes) {
        ComponentValidator.getInstance(checkBox).ifPresent {
          it.revalidate()
        }
      }
    }

    return this
  }
}
