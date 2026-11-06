package com.intellij.database.csv.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.view.editors.DataGridEditorUtil
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable

internal class CsvRecordFormatFormUi(quotesTable: JBTable) {
  lateinit var valueSeparatorComboBox: ComboBox<String>
  lateinit var recordSeparatorComboBox: ComboBox<String>
  lateinit var nullTextCombo: ComboBox<String>
  lateinit var recordPrefixTextField: JBTextField
  lateinit var recordSuffixTextField: JBTextField
  lateinit var quotationPolicyComboBox: ComboBox<String>
  lateinit var trimWhitespaceCheckBox: JBCheckBox

  private lateinit var addRowPrefixSuffixRow: Row
  private lateinit var rowPrefixSuffixRows: RowsRange

  @JvmField
  val panel: DialogPanel = panel {
    row(DataGridBundle.message("settings.value.separator")) {
      valueSeparatorComboBox = editableComboBox()
    }
    row(DataGridBundle.message("settings.row.separator")) {
      recordSeparatorComboBox = editableComboBox()
    }
    row(DataGridBundle.message("settings.null.value.text")) {
      nullTextCombo = editableComboBox()
    }
    addRowPrefixSuffixRow = row {
      link(DataGridBundle.message("settings.add.row.prefix.suffix")) { setRowPrefixSuffixVisible(true) }
    }
    rowPrefixSuffixRows = rowsRange {
      row(DataGridBundle.message("settings.row.prefix")) {
        recordPrefixTextField = textField()
          .align(AlignX.FILL)
          .component
      }
      row(DataGridBundle.message("settings.row.suffix")) {
        recordSuffixTextField = textField()
          .align(AlignX.FILL)
          .component
      }
    }
    row {
      cell(DataGridEditorUtil.labeledDecorator(JBLabel(DataGridBundle.message("settings.quotation")), quotesTable))
        .align(AlignX.FILL)
    }
    row(DataGridBundle.message("settings.quote.values")) {
      quotationPolicyComboBox = comboBox(emptyList<String>())
        .component
    }.layout(RowLayout.INDEPENDENT)
    row {
      trimWhitespaceCheckBox = checkBox(DataGridBundle.message("settings.trim.whitespaces")).component
    }
  }

  fun setRowPrefixSuffixVisible(visible: Boolean) {
    addRowPrefixSuffixRow.visible(!visible)
    rowPrefixSuffixRows.visible(visible)
  }

  private fun Row.editableComboBox(): ComboBox<String> {
    return comboBox(emptyList<String>())
      .align(AlignX.FILL)
      .applyToComponent { isEditable = true }
      .component
  }
}

internal class QuotesRowEditorUi {
  lateinit var leftQuoteTextField: JBTextField
  lateinit var rightQuoteTextField: JBTextField
  lateinit var escapeMethodComboBox: ComboBox<String>

  @JvmField
  val panel: DialogPanel = panel {
    row {
      leftQuoteTextField = textField()
        .label(DataGridBundle.message("csv.format.settings.quotes.left"), LabelPosition.TOP)
        .columns(4)
        .component
      rightQuoteTextField = textField()
        .label(DataGridBundle.message("csv.format.settings.quotes.right"), LabelPosition.TOP)
        .columns(4)
        .component
      escapeMethodComboBox = comboBox(emptyList<String>())
        .label(DataGridBundle.message("csv.format.settings.quotes.escape"), LabelPosition.TOP)
        .applyToComponent { isEditable = true }
        .component
    }
  }
}
