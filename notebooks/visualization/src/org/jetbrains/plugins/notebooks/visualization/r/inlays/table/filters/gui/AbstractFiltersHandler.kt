/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui

import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.AndFilter
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.Filter
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.IFilter
import org.jetbrains.plugins.notebooks.visualization.r.inlays.table.filters.gui.editor.FilterEditor
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JTable
import javax.swing.RowFilter
import javax.swing.RowSorter
import javax.swing.SwingUtilities
import javax.swing.event.RowSorterEvent
import javax.swing.event.RowSorterListener

abstract class AbstractFiltersHandler : AndFilter(), PropertyChangeListener {
  private var sendNotifications = 0
  private var pendingNotifications: Boolean = false
  private val autoSelector = AutoSelector()
  private val editors = HashMap<Int, FilterEditor>()
  protected var sorter: RowSorter<*>? = null

  var parserModel: IParserModel? = null
    set(newParserModel) {
      if (newParserModel != null && newParserModel !== field) {
        field?.removePropertyChangeListener(this)
        field = newParserModel
        newParserModel.addPropertyChangeListener(this)
        enableNotifications(false)
        editors.values.forEach { it.resetFilter() }
        enableNotifications(true)
      }
      field = newParserModel
    }

  var table: JTable? = null
    set(table) {
      choicesHandler.setInterrupted(true)
      val oldTable = this.table
      field = table
      autoSelector.replacedTable(oldTable, table)
    }
  protected abstract var currentFilter: RowFilter<*, *>?
  protected abstract val choicesHandler: ChoicesHandler
  abstract var isFilterOnUpdates: Boolean
  abstract var isAdaptiveChoices: Boolean

  private var applyingFilter: Filter? = null
  private var onWarning: Boolean = false

  var isAutoSelection: Boolean
    get() = autoSelector.enabled
    set(enable) {
      autoSelector.enabled = enable
    }

  var autoChoices: AutoChoices? = null
    set(mode) {
      if (mode != field) {
        enableNotifications(false)
        field = mode
        for (editor in editors.values) {
          editor.autoChoices = mode
        }
        enableNotifications(true)
      }
    }

  init {
    addFilterObserver { notifyUpdatedFilter() }
  }

  override fun propertyChange(evt: PropertyChangeEvent) {
    val parserModel = parserModel ?: return
    val target: Class<*>?
    var formatChange = false
    if (IParserModel.IGNORE_CASE_PROPERTY == evt.propertyName) {
      target = null
    } else {
      if (IParserModel.FORMAT_PROPERTY == evt.propertyName) {
        formatChange = true
      }
      else if (IParserModel.COMPARATOR_PROPERTY != evt.propertyName) {
        return
      }
      val cl = evt.newValue
      if (cl is Class<*>) {
        target = cl
      } else {
        return
      }
    }

    enableNotifications(false)
    for (editor in editors.values) {
      if (target == null) {
        editor.isIgnoreCase = parserModel.isIgnoreCase
      }
      else if (editor.modelClass == target) {
        if (formatChange) {
          editor.format = parserModel.getFormat(target)
        } else {
          editor.comparator = parserModel.getComparator(target)
        }
      }
    }

    enableNotifications(true)
  }

  override fun setEnabled(enabled: Boolean) {
    enableNotifications(false)
    super.setEnabled(enabled)
    enableNotifications(true)
  }

  override fun addFilter(vararg filtersToAdd: IFilter) {
    choicesHandler.filterOperation(true)
    super.addFilter(*filtersToAdd)
    choicesHandler.filterOperation(false)
  }

  override fun removeFilter(vararg filtersToRemove: IFilter) {
    choicesHandler.filterOperation(true)
    super.removeFilter(*filtersToRemove)
    choicesHandler.filterOperation(false)
  }

  fun addFilterEditor(editor: FilterEditor) {
    super.addFilter(editor.filter)
    editors[editor.modelIndex] = editor
    editor.autoChoices = autoChoices
  }

  fun removeFilterEditor(editor: FilterEditor) {
    super.removeFilter(editor.filter)
    editors.remove(editor.modelIndex)
  }

  fun updateEditorChoices(editor: FilterEditor) {
    if (editors.containsValue(editor) && isEnabled) {
      choicesHandler.editorUpdated(editor)
    }
  }

  override fun filterUpdated(filter: IFilter) {
    val wasEnabled = isEnabled
    val filterWasDisabled = isDisabled(filter)
    if (filter !== applyingFilter) {
      choicesHandler.filterUpdated(filter, false)
    }

    super.filterUpdated(filter)
    if (filterWasDisabled && filter.isEnabled) {
      choicesHandler.filterEnabled(filter)
    }
    else if (wasEnabled && !isEnabled) {
      choicesHandler.allFiltersDisabled()
    }
  }

  fun applyEditorFilter(filter: Filter): Boolean {
    val ret = choicesHandler.filterUpdated(filter, true)
    if (ret) {
      applyingFilter = filter
      filter.reportFilterUpdatedToObservers()
      applyingFilter = null
    }

    return ret
  }

  fun consolidateFilterChanges(modelIndex: Int): Boolean {
    choicesHandler.consolidateFilterChanges(modelIndex)
    return onWarning
  }

  fun updateTableFilter() {
    pendingNotifications = sorter == null
    if (sorter != null) {
      val rf = if (isEnabled()) choicesHandler.getRowFilter() else null
      currentFilter = rf
      checkWarningState()
    }
  }

  fun getEditors(): Collection<FilterEditor> {
    return editors.values
  }

  fun getEditor(column: Int): FilterEditor {
    return editors[column]!!
  }

  fun enableNotifications(enable: Boolean) {
    sendNotifications += if (enable) 1 else -1
    if (enable) {
      if (sendNotifications == 0) {
        if (choicesHandler.setInterrupted(false) || pendingNotifications) {
          updateTableFilter()
        }
      }
    } else if (choicesHandler.setInterrupted(true)) {
      // updateTableFilter();
    }
  }

  fun notifyUpdatedFilter() {
    if (sendNotifications < 0) {
      pendingNotifications = true
    } else {
      updateTableFilter()
    }
  }

  fun tableUpdated() {
    checkWarningState()
  }

  private fun checkWarningState() {
    val warning = this.table!!.rowCount == 0 && this.table!!.model.rowCount > 0
    if (warning != this.onWarning) {
      this.onWarning = warning
      for (editor in getEditors()) {
        editor.setWarning(warning)
      }
    }
  }

  fun updateModel() {
    autoSelector.setSorter(table)
  }

  private inner class AutoSelector : RowSorterListener, Runnable, PropertyChangeListener {
    var enabled = FilterSettings.autoSelection
      set(enable) {
        if (field != enable) {
          if (enable) {
            sorter?.addRowSorterListener(this)
          } else {
            sorter?.removeRowSorterListener(this)
          }
        }
        field = enable
      }


    fun replacedTable(oldTable: JTable?, newTable: JTable?) {
      val event = "rowSorter"
      oldTable?.removePropertyChangeListener(event, this)

      newTable?.addPropertyChangeListener(event, this)
      setSorter(newTable)
    }

    override fun propertyChange(evt: PropertyChangeEvent) {
      setSorter(evt.source as JTable)
    }

    fun setSorter(table: JTable?) {
      sorter?.removeRowSorterListener(this)
      currentFilter = null
      sorter = null
      val rowSorter = table?.rowSorter
      sorter = rowSorter
      notifyUpdatedFilter()
      if (enabled) {
        rowSorter?.addRowSorterListener(this)
      }
      isFilterOnUpdates = isFilterOnUpdates
    }

    override fun run() {
      if (sorter?.viewRowCount == 1) {
        table?.selectionModel?.setSelectionInterval(0, 0)
      }
    }

    override fun sorterChanged(e: RowSorterEvent) {
      if (e.type == RowSorterEvent.Type.SORTED && e.source.viewRowCount == 1) {
        SwingUtilities.invokeLater(this)
      }
    }
  }

}
