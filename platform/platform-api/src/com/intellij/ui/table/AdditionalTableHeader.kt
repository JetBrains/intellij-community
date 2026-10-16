// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.table

import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel


abstract class AdditionalTableHeader : JPanel(BorderLayout()) {
  enum class Position {
    TOP,
    INLINE,
    NONE,
    REPLACE
  }

  abstract var table: JTable?
    protected set

  private val updateChangeListener = PropertyChangeListener { evt: PropertyChangeEvent ->
    if ("model" == evt.propertyName || "componentOrientation" == evt.propertyName) {
      removeController()
      recreateController()
    }
  }

  var position: Position?
    /** Returns the mode currently associated to the TableHeader.  */
    get() = positionHelper.position
    /** Sets the position of the header related to the table.  */
    set(location) {
      positionHelper.position = location
    }

  protected var columnsController: ColumnsControllerPanel? = null

  /** The helper to handle the location of the additional header in the table header.  */
  private val positionHelper: AdditionalTableHeaderPositionHelper = AdditionalTableHeaderPositionHelper(this)

  protected val resizer: ComponentAdapter = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      columnsController?.revalidate()
    }
  }

  open fun installTable(table: JTable?) {
    val oldTable = this.table
    disposeOldTable(oldTable, table)

    this.table = table

    setupNewTable(table)
  }

  private fun setupNewTable(newTable: JTable?) {
    removeController()
    if (newTable == null) {
      revalidate()
    }
    else {
      recreateController()
      newTable.addComponentListener(resizer)
      newTable.addPropertyChangeListener("model", updateChangeListener)
      newTable.addPropertyChangeListener("componentOrientation", updateChangeListener)
    }
  }

  fun disposeOldTable(oldTable: JTable?, newTable: JTable?) {
    changeTableAtPositionHelper(oldTable, newTable)
    if (oldTable != null) {
      oldTable.removeComponentListener(resizer)
      oldTable.removePropertyChangeListener("model", updateChangeListener)
      oldTable.removePropertyChangeListener("componentOrientation", updateChangeListener)
    }
  }

  protected fun changeTableAtPositionHelper(oldTable: JTable?, newTable: JTable?) {
    positionHelper.changeTable(oldTable, newTable)
  }

  abstract fun detachController()

  protected fun removeController() {
    if (columnsController != null) {
      detachController()
      remove(columnsController)
      columnsController = null
    }
  }

  abstract fun createColumnsController(): ColumnsControllerPanel

  protected open fun recreateController() {
    val newController = createColumnsController()
    columnsController = newController
    add(newController, BorderLayout.WEST)
    revalidate()
  }

  /** Method automatically invoked when the class ancestor changes.  */
  override fun addNotify() {
    super.addNotify()
    positionHelper.currentHeaderContainmentUpdate()
  }

  /** Hides / makes visible the header.  */
  override fun setVisible(flag: Boolean) {
    if (isVisible != flag) {
      positionHelper.headerVisibilityChanged(flag)
    }
    super.setVisible(flag)
    positionHelper.headerVisibilityChanged(flag)
  }

  abstract class ColumnsControllerPanel(val table: JTable) : ColumnHeaderController<ColumnsControllerPanel.AdditionalPanel>(
    object : Context {
      override val columnModel: TableColumnModel get() = table.columnModel
      override val model: TableModel get() = table.model
      override val componentOrientation: ComponentOrientation get() = table.componentOrientation
      override val draggedColumn: TableColumn? get() = table.tableHeader.draggedColumn
      override val draggedDistance: Int get() = table.tableHeader.draggedDistance
    },
    ignoresObsoleteEvents = false,
  ) {
    abstract override fun createColumn(columnView: Int): AdditionalPanel

    abstract inner class AdditionalPanel(tableColumn: TableColumn) : ColumnPanel(tableColumn, ::placeComponents)
  }
}
