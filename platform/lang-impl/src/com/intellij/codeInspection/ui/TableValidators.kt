// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui

import com.intellij.codeInspection.options.StringValidator
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.cellvalidators.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.fields.ExtendableTextField
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import javax.swing.DefaultCellEditor
import javax.swing.event.TableModelEvent
import javax.swing.table.DefaultTableCellRenderer

private class BackgroundValidationsHolder(val context: Disposable, val project: Project, val table: ListTable) {

  private val cache: ConcurrentHashMap<Pair<Int, Int>, ValidationResult> = ConcurrentHashMap()
  private val validators: ConcurrentHashMap<Int, StringValidator> = ConcurrentHashMap()
  private val cs = CoroutineScope(SupervisorJob())
  private val model: ListWrappingTableModel = table.model

  init {
    Disposer.register(context) {
      cs.cancel()
    }
  }

  fun delete(column: Int, firstRow: Int, lastRow: Int) {
    for (row in firstRow..lastRow) {
      cache.remove(Pair(row, column))
    }
  }

  private fun checkInBackground(row: Int, column: Int, dumb: Boolean, stringValue: String, validator: StringValidator) {
    cs.launch(Dispatchers.Default) {
      readAction {
        val errorMessage = validator.getErrorMessage(project, stringValue)
        cache[Pair(row, column)] = ValidationResult(dumb, stringValue, errorMessage)
      }

      ApplicationManager.getApplication().invokeLater({
                                                        table.repaint()
                                                      }, ModalityState.any())
    }
  }

  fun check(column: Int, firstRow: Int, lastRow: Int) {
    val rowCount = model.rowCount
    val columnCount = model.columnCount
    if (rowCount == 0 || columnCount == 0 || firstRow > lastRow) return
    if (column >= columnCount || lastRow >= rowCount || firstRow < 0 || column < -1) return
    if (column == -1) {
      for (nextColumn in 0 until columnCount) {
        check(nextColumn, firstRow, lastRow)
      }
      return
    }
    for (row in firstRow..lastRow) {
      val value = (model.getValueAt(row, column) as? String) ?: continue
      val validator = validators[column] ?: continue
      checkInBackground(row, column, DumbService.isDumb(project), value, validator)
    }
  }

  fun getResult(row: Int, column: Int, dumb: Boolean): ValidationResult? {
    val validationResult = cache[Pair(row, column)]
    if (validationResult != null && validationResult.isDumbMode != dumb) {
      init()
    }
    return validationResult
  }

  fun setValidator(index: Int, validator: StringValidator) {
    validators[index] = validator
  }

  fun init() {
    for (column in 0 until model.columnCount) {
      check(column, 0, model.rowCount - 1)
    }
  }
}

private data class ValidationResult(val isDumbMode: Boolean = false, val value: String = "", var errorMessage: String?)

internal fun addColumnValidators(table: ListTable, components: List<StringValidator?>, parent: Disposable?, project: Project?) {
  if (project == null || parent == null) return
  var hasInstalledValidators = false
  val validationHolder = BackgroundValidationsHolder(parent, project, table)
  for ((index, validator) in components.withIndex()) {
    validator ?: continue
    hasInstalledValidators = true
    validationHolder.setValidator(index, validator)
    val cellEditor = ExtendableTextField()
    cellEditor.putClientProperty(DarculaUIUtil.COMPACT_PROPERTY, true)

    val column = table.columnModel.getColumn(index)
    ComponentValidator(parent)
      .withValidator(Supplier {
        val text = cellEditor.text ?: return@Supplier null
        val errorMessage = validator.getErrorMessage(project, text)
        if (errorMessage != null) {
          ValidationUtils.setExtension(cellEditor, ValidationUtils.WARNING_EXTENSION, true)
          ValidationInfo(errorMessage, cellEditor).asWarning()
        }
        else {
          ValidationUtils.setExtension(cellEditor, ValidationUtils.WARNING_EXTENSION, false)
          null
        }
      }
      )
      .andRegisterOnDocumentListener(cellEditor)
      .installOn(cellEditor)


    column.cellEditor = DefaultCellEditor(cellEditor)
    column.cellRenderer = ValidatingTableCellRendererWrapper(DefaultTableCellRenderer())
      .bindToEditorSize {
        cellEditor.preferredSize
      }
      .withCellValidator(object : TableCellValidator {
        override fun validate(value: Any?, row: Int, column: Int): ValidationInfo? {
          val stringValue = (value as? String) ?: ""
          val dumb = DumbService.isDumb(project)
          val previous = validationHolder.getResult(row, column, dumb)
          if (previous != null && stringValue == previous.value && dumb == previous.isDumbMode) {
            @NlsSafe val previousResult = previous.errorMessage ?: return null
            return ValidationInfo(previousResult, null).asWarning()
          }
          return null
        }
      })
  }
  if (hasInstalledValidators) {
    table.model.addTableModelListener {
      if (it == null) return@addTableModelListener
      if (it.type == TableModelEvent.DELETE) validationHolder.delete(it.column, it.firstRow, it.lastRow)
      if (it.type == TableModelEvent.INSERT || it.type == TableModelEvent.UPDATE) validationHolder.check(it.column, it.firstRow, it.lastRow)
    }
    validationHolder.init()

    CellTooltipManager(parent)
      .withCellComponentProvider(CellComponentProvider.forTable(table))
      .installOn(table)
  }
}