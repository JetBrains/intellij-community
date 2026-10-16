// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.table

import com.intellij.util.ui.EDT
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.LinkedList
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.ChangeEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.TableColumnModelEvent
import javax.swing.event.TableColumnModelListener
import javax.swing.table.TableColumn
import javax.swing.table.TableColumnModel
import javax.swing.table.TableModel

/** Keeps additional column panels aligned without exposing the table component. */
abstract class ColumnHeaderController<P : ColumnHeaderController.ColumnPanel> internal constructor(
  private val context: Context,
  /** False keeps the behavior that subclasses of [AdditionalTableHeader.ColumnsControllerPanel] expect. */
  private val ignoresObsoleteEvents: Boolean,
) : JPanel(null), TableColumnModelListener {
  /** Creates a controller that ignores events and queued updates after [detach] or after a model is replaced. */
  @ApiStatus.Internal
  constructor(context: Context) : this(context, ignoresObsoleteEvents = true)

  /** Supplies the models and measurements used to place column panels. */
  @ApiStatus.Internal
  interface Context {
    val columnModel: TableColumnModel
    val model: TableModel
    val componentOrientation: ComponentOrientation
    val draggedColumn: TableColumn?
    val draggedDistance: Int
  }

  private var detached = false
  protected lateinit var myPreferredSize: Dimension
  val columns: LinkedList<P> = LinkedList()
  protected val tableColumnModel: TableColumnModel = context.columnModel
  protected val tableModel: TableModel = context.model

  @JvmField
  protected var autoRun: Int = 0

  protected val isCorrectModel: Boolean
    get() = tableModel === context.model && (!ignoresObsoleteEvents || !detached && tableColumnModel === context.columnModel)

  private val isObsolete: Boolean
    get() = ignoresObsoleteEvents && !isCorrectModel

  abstract fun createColumn(columnView: Int): P

  /** Removes column listeners and releases the panels. */
  fun detach() {
    if (detached) return
    detached = true
    tableColumnModel.removeColumnModelListener(this)
    for (column in columns) column.detach()
    if (ignoresObsoleteEvents) {
      columns.clear()
      removeAll()
    }
  }

  protected fun update() {
    if (isObsolete) return
    autoRun++
    if (EDT.isCurrentThreadEdt()) {
      SwingUtilities.invokeLater { if (!isObsolete) updateColumns() }
    }
    else {
      updateColumns()
    }
  }

  abstract fun updateColumns()

  override fun columnAdded(e: TableColumnModelEvent) {
    if (!isCorrectModel) return
    createColumn(e.toIndex)
    update()
  }

  override fun columnRemoved(e: TableColumnModelEvent) {
    if (!isCorrectModel) return
    val panel = columns.removeAt(e.fromIndex)
    panel.detach()
    remove(panel)
    update()
  }

  override fun columnSelectionChanged(e: ListSelectionEvent) {}

  protected fun placeComponents() {
    if (isObsolete) return
    var x = 0
    val iterator = if (context.componentOrientation.isLeftToRight) columns.iterator() else columns.descendingIterator()
    while (iterator.hasNext()) {
      val panel = iterator.next()
      panel.setBounds(x, 0, panel.myWidth, myPreferredSize.height)
      x += panel.myWidth
    }
    revalidate()
  }

  abstract fun computeMyPreferredSize(): Dimension

  override fun columnMarginChanged(e: ChangeEvent) {
    placeComponents()
  }

  override fun columnMoved(e: TableColumnModelEvent) {
    if (isObsolete) return
    if (e.fromIndex != e.toIndex) {
      val panel = columns.removeAt(e.fromIndex)
      columns.add(e.toIndex, panel)
      placeComponents()
    }
    val draggedColumn = context.draggedColumn ?: return
    val rightToLeft = !context.componentOrientation.isLeftToRight
    val iterator = if (rightToLeft) columns.descendingIterator() else columns.iterator()
    var previous: P? = null
    while (iterator.hasNext()) {
      val panel = iterator.next()
      if (panel.tableColumn === draggedColumn) {
        val bounds = panel.bounds
        val x = previous?.let { it.x + it.width } ?: 0
        bounds.x = x + context.draggedDistance
        panel.bounds = bounds
        if (rightToLeft) previous = if (iterator.hasNext()) iterator.next() else null
        if (previous != null) {
          val previousOrder = getComponentZOrder(previous)
          val order = getComponentZOrder(panel)
          val overPrevious = if (rightToLeft) context.draggedDistance > 0 else context.draggedDistance < 0
          if (overPrevious != (order < previousOrder)) {
            setComponentZOrder(previous, order)
            setComponentZOrder(panel, previousOrder)
          }
        }
        break
      }
      previous = panel
    }
  }

  /** Tracks the size of one column panel. */
  abstract class ColumnPanel @ApiStatus.Internal constructor(
    val tableColumn: TableColumn,
    private val widthChanged: () -> Unit,
  ) : JPanel(BorderLayout()), PropertyChangeListener {
    var myWidth: Int = 0
    var myHeight: Int = 0

    abstract fun detach()

    override fun propertyChange(evt: PropertyChangeEvent) {
      val width = tableColumn.width
      if (myWidth != width) {
        myWidth = width
        widthChanged()
      }
    }

    abstract fun updateHeight()
  }
}
