// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.table

import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.JTableHeader
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

  abstract class ColumnsControllerPanel(val table: JTable) : JPanel(null), TableColumnModelListener {

    protected lateinit var myPreferredSize: Dimension

    /** The list of columns, sorted in the view way.  */
    val columns: LinkedList<AdditionalPanel> = LinkedList()

    /**
     * The panel must keep a reference to the TableColumnModel, to be able
     * to 'unregister' when the controller is destroyed.
     */
    protected val tableColumnModel: TableColumnModel = table.columnModel

    /**
     * Variable keeping track of the number of times that the updateColumns() method
     * is going to be invoked from the gui thread.
     */
    @JvmField
    protected var autoRun: Int = 0

    /**
     * The model associated to the table when the controller is created.
     */
    protected val tableModel: TableModel = table.model

    protected val isCorrectModel: Boolean
      get() = tableModel === table.model

    abstract fun createColumn(columnView: Int): AdditionalPanel

    /** Detaches the current instance from any registered listeners.  */
    fun detach() {
      for (column in columns) {
        column.detach()
      }
      tableColumnModel.removeColumnModelListener(this)
    }

    /**
     * Updates the columns. If this is the GUI thread, better wait until all
     * the events have been handled. Otherwise, do it immediately, as it is
     * not known how the normal/Gui thread can interact
     */
    protected fun update() {
      autoRun += 1
      if (SwingUtilities.isEventDispatchThread()) {
        SwingUtilities.invokeLater(Runnable { this.updateColumns() })
      }
      else {
        updateColumns()
      }
    }

    abstract fun updateColumns()

    /** [TableColumnModelListener] interface.  */
    override fun columnAdded(e: TableColumnModelEvent) {
      //Support the case where a model is being changed
      if (isCorrectModel) {
        createColumn(e.toIndex)
        update()
      }
    }

    /** [TableColumnModelListener] interface.  */
    override fun columnRemoved(e: TableColumnModelEvent) {
      //Support the case where a model is being changed
      if (isCorrectModel) {
        val fcp = columns.removeAt(e.fromIndex)
        fcp.detach()
        remove(fcp)
        update()
      }
    }

    /** [TableColumnModelListener] interface. Do nothing.  */
    override fun columnSelectionChanged(e: ListSelectionEvent) {}

    /**
     * Places all the components in line, respecting their preferred widths.
     */
    protected fun placeComponents() {
      var x = 0
      val it = when (ComponentOrientation.RIGHT_TO_LEFT) {
        table.componentOrientation -> columns.descendingIterator()
        else -> columns.iterator()
      }

      while (it.hasNext()) {
        val fcp = it.next()
        fcp.setBounds(x, 0, fcp.myWidth, myPreferredSize.height)
        x += fcp.myWidth
      }
      revalidate()
    }

    abstract fun computeMyPreferredSize(): Dimension

    /** [TableColumnModelListener] interface.  */
    override fun columnMarginChanged(e: ChangeEvent) {
      placeComponents()
    }

    /** [TableColumnModelListener] interface.  */
    override fun columnMoved(e: TableColumnModelEvent) {
      if (e.fromIndex != e.toIndex) {
        val fcp = columns.removeAt(e.fromIndex)
        columns.add(e.toIndex, fcp)
        placeComponents()
      }
      // previous block places each filter column in the right position
      // BUT does not take in consideration the dragging distance
      val header: JTableHeader = table.getTableHeader()
      val tc = header.draggedColumn
      if (tc != null) {
        val rightToLeft = table.getComponentOrientation() ==
          ComponentOrientation.RIGHT_TO_LEFT
        // Iterate the filter columns, we need to know the previous
        // and the current column
        val it = if (rightToLeft) columns.descendingIterator() else columns.iterator()
        var previous: AdditionalPanel? = null
        while (it.hasNext()) {
          val fcp = it.next()
          if (fcp.tableColumn === tc) {
            var r: Rectangle? = null
            var x = 0.0
            if (previous != null) {
              r = previous.bounds
              // obtain on X the position that the current
              // dragged column should be IF there would be no dragging
              // (previous panel plus its width)
              x = r.getX() + r.getWidth()
            }
            // shift now the column to the correct distance
            r = fcp.getBounds(r)
            r.translate((x - r.getX() + header.draggedDistance).toInt(), 0)
            fcp.bounds = r

            // one detail is left: the Z order of this column should be lower that the Z order of the column being dragged over
            if (rightToLeft) {
              // in this case, previous is the next column, not the one before!
              previous = if (it.hasNext()) it.next() else null
            }
            if (previous != null) {
              val prevZOrder = getComponentZOrder(previous)
              val zOrder = getComponentZOrder(fcp)
              val overPreviousDragging = if (rightToLeft) header.draggedDistance > 0 else header.draggedDistance < 0
              if (overPreviousDragging != zOrder < prevZOrder) {
                setComponentZOrder(previous, zOrder)
                setComponentZOrder(fcp, prevZOrder)
              }
            }
            break
          }
          previous = fcp
        }
      }
    }

    abstract inner class AdditionalPanel(val tableColumn: TableColumn) : JPanel(BorderLayout()), PropertyChangeListener {
      var myWidth: Int = 0
      var myHeight: Int = 0

      /** Performs any cleaning required before removing this component. */
      abstract fun detach()

      /** [PropertyChangeListener] interface. Listening for changes on the width of the table's column.  */
      override fun propertyChange(evt: PropertyChangeEvent) {
        // just listen for any property
        val newW = tableColumn.width
        if (myWidth != newW) {
          myWidth = newW
          placeComponents()
        }
      }

      abstract fun updateHeight()

    }
  }
}