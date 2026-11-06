package com.intellij.database.csv.ui

import com.intellij.database.DataGridBundle
import com.intellij.database.csv.CsvFormat
import com.intellij.database.csv.CsvFormatEditor
import com.intellij.database.csv.CsvRecordFormat
import com.intellij.openapi.Disposable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected
import com.intellij.util.EventDispatcher
import java.util.EventListener
import javax.swing.JComponent

class CsvFormatForm(parent: Disposable, settings: CsvFormatUISettings) : CsvFormatEditor {

  private lateinit var headerFormatCheckBox: JBCheckBox
  private lateinit var rowNumbersCheckBox: JBCheckBox

  private var formatName: String = ""
  private var formatId: String = ""
  private val recordFormatForm = CsvRecordFormatForm(parent)
  private val headerFormatForm = CsvRecordFormatForm(parent)

  private var resetting = false
  private val eventDispatcher = EventDispatcher.create(ChangeListener::class.java)

  val component: JComponent
    field = panel {
      row {
        cell(recordFormatForm.mainPanel)
          .align(AlignX.FILL)
      }
      row {
        headerFormatCheckBox = checkBox(DataGridBundle.message("csv.format.settings.first.row.is.header")).component
      }
      row {
        rowNumbersCheckBox = checkBox(DataGridBundle.message("csv.format.settings.first.column.is.header")).component
      }
      if (settings.isHeaderSettingsVisible) {
        group(DataGridBundle.message("csv.format.settings.header.format.label"), indent = false) {
          row {
            cell(headerFormatForm.mainPanel)
              .align(AlignX.FILL)
          }
        }.visibleIf(headerFormatCheckBox.selected)
      }
    }

  val format: CsvFormat
    get() {
      val dataFormat = recordFormatForm.format
      val headerFormat = if (headerFormatCheckBox.isSelected) headerFormatForm.format else null
      return CsvFormat(formatName, dataFormat, headerFormat, formatId, rowNumbersCheckBox.isSelected)
    }

  init {
    headerFormatCheckBox.addItemListener {
      if (headerFormatCheckBox.isSelected) {
        headerFormatForm.reset(recordFormatForm.format)
      }
      else {
        fireFormatChangedEvent()
      }
    }
    rowNumbersCheckBox.addItemListener { fireFormatChangedEvent() }

    val changeEventPropagator = CsvRecordFormatForm.ChangeListener { fireFormatChangedEvent() }
    headerFormatForm.addChangeListener(changeEventPropagator)
    recordFormatForm.addChangeListener(changeEventPropagator)
  }

  fun reset(format: CsvFormat) {
    resetting = true
    try {
      formatName = format.name
      formatId = format.id
      recordFormatForm.reset(format.dataRecord)
      setHeaderFormat(format.headerRecord)
      rowNumbersCheckBox.isSelected = format.rowNumbers
    }
    finally {
      resetting = false
      fireFormatChangedEvent()
    }
  }

  fun addChangeListener(listener: ChangeListener) {
    eventDispatcher.addListener(listener)
  }

  private fun fireFormatChangedEvent() {
    if (!resetting) {
      eventDispatcher.multicaster.formatChanged(this)
    }
  }

  private fun setHeaderFormat(headerFormat: CsvRecordFormat?) {
    headerFormatCheckBox.isSelected = headerFormat != null
    if (headerFormat != null) {
      headerFormatForm.reset(headerFormat)
    }
  }

  override fun firstRowIsHeader(): Boolean {
    return headerFormatCheckBox.isSelected
  }

  override fun setFirstRowIsHeader(value: Boolean) {
    headerFormatCheckBox.isSelected = value
  }

  fun interface ChangeListener : EventListener {
    fun formatChanged(source: CsvFormatForm)
  }
}
