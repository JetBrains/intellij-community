// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.COMBOBOX_COLUMN_SIZE
import com.intellij.platform.feedback.dialog.createBoldJBLabel
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.toMutableProperty
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.xml.util.XmlStringUtil
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import org.jetbrains.annotations.NonNls

class ComboBoxBlock private constructor(
  @NlsContexts.Label private val myLabel: String,
  private val myItems: List<ComboBoxItemData>,
  private val myJsonElementName: String,
  @Suppress("UNUSED_PARAMETER") marker: Unit,
) : FeedbackBlock, TextDescriptionProvider, JsonDataProvider {

  @Suppress("HardCodedStringLiteral")
  constructor(
    @NlsContexts.Label myLabel: String,
    myItems: List<String>,
    myJsonElementName: String,
  ) : this(myLabel, myItems.map { ComboBoxItemData(it, it) }, myJsonElementName, Unit)

  constructor(
    @NlsContexts.Label label: String,
    items: Collection<ComboBoxItemData>,
    jsonElementName: String,
  ) : this(label, items.toList(), jsonElementName, Unit)

  private var myProperty: ComboBoxItemData? = null
  private var myComment: @NlsContexts.DetailedDescription String? = null
  private var myColumnSize: Int = COMBOBOX_COLUMN_SIZE
  private var myRandomizeOptionOrder: Boolean = false
  private var myUseAlignFill: Boolean = false
  private var myUseWrappingLabel: Boolean = false

  override fun addToPanel(panel: Panel) {
    val items = if (myRandomizeOptionOrder) myItems.shuffled() else myItems

    panel.apply {
      // A long label rendered as a single-line JBLabel forces the whole dialog to become very wide,
      // so it can optionally be shown as a word-wrapped label above the combo box instead.
      if (myUseWrappingLabel) {
        row {
          text("<b>${XmlStringUtil.escapeString(myLabel)}</b>", maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
        }.bottomGap(BottomGap.NONE)
      }
      row {
        comboBox(items, textListCellRenderer { it?.label.orEmpty() })
          .apply {
            if (!myUseWrappingLabel) {
              label(createBoldJBLabel(myLabel), LabelPosition.TOP)
            }
          }
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
      appendLine(myProperty?.label.orEmpty())
      appendLine()
    }
  }

  override fun collectBlockDataToJson(jsonObjectBuilder: JsonObjectBuilder) {
    jsonObjectBuilder.apply {
      put(myJsonElementName, myProperty?.jsonValue.orEmpty())
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

  /**
   * Renders the label as a word-wrapped text above the combo box instead of a single-line label,
   * so a long question does not stretch the whole dialog horizontally.
   */
  fun useWrappingLabel(): ComboBoxBlock {
    myUseWrappingLabel = true
    return this
  }

  fun randomizeOptionOrder(): ComboBoxBlock {
    myRandomizeOptionOrder = true
    return this
  }
}

data class ComboBoxItemData(
  @NlsContexts.Label val label: String,
  @NonNls val jsonValue: String,
) {
  override fun toString(): String = label
}
