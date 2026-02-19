// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.table

import java.awt.BorderLayout
import java.awt.Component
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JViewport
import javax.swing.table.JTableHeader


/**
 * Helper class to locate the additional header on the expected place by the table header
 */
class AdditionalTableHeaderPositionHelper(val statisticsHeader: AdditionalTableHeader) : PropertyChangeListener {
  /** This variable defines how to handle the position of the header.  */
  var myLocation: AdditionalTableHeader.Position? = null

  /**
   * The viewport associated to this header. It is null if the location is not
   * automatically managed
   */
  private var headerViewport: JViewport? = null

  /** The previous viewport of the associated table.  */
  private var previousTableViewport: Component? = null

  var position: AdditionalTableHeader.Position?
    /** Returns the mode currently associated to the TableHeader.  */
    get() = myLocation
    /** Sets the position of the header related to the table.  */
    set(location) {
      myLocation = location
      val table = statisticsHeader.table
      changeTable(table, table)
    }

  fun headerVisibilityChanged(visible: Boolean) {
    val table = statisticsHeader.table
    changeTable(table, null)
    if (visible && table != null) {
      changeTable(null, table)
    }
  }

  /**
   * The additional header reports that the table being handled is going to
   * change.
   */
  fun changeTable(oldTable: JTable?, newTable: JTable?) {
    oldTable?.removePropertyChangeListener("ancestor", this)
    cleanUp()
    if (newTable != null) {
      newTable.addPropertyChangeListener("ancestor", this)
      trySetUp(newTable)
    }
  }

  /** Method automatically invoked when the class ancestor changes.  */
  fun currentHeaderContainmentUpdate() {
    if (!canHeaderLocationBeManaged()) {
      cleanUp()
    }
  }

  /** [PropertyChangeListener] interface.  */
  override fun propertyChange(evt: PropertyChangeEvent) {
    // the table has changed containment. clean up status and prepare again,
    // if possible; however, do nothing if the current setup is fine
    if (previousTableViewport !== evt.newValue && evt.source !== statisticsHeader.table) {
      previousTableViewport = null
      cleanUp()
      trySetUp(statisticsHeader.table)
    }
  }

  /**
   * Returns true if the header location can be automatically controlled.
   *
   * @return  false if the component has been explicitly included in a
   * container
   */
  private fun canHeaderLocationBeManaged(): Boolean {
    if (myLocation == AdditionalTableHeader.Position.NONE) {
      return false
    }
    val parent = statisticsHeader.parent
    return parent == null || parent === headerViewport
  }

  /** Tries to setup the filter header automatically for the given table.  */
  private fun trySetUp(table: JTable?) {
    if (table != null && table.isVisible && canHeaderLocationBeManaged()
        && statisticsHeader.isVisible) {
      val p = table.parent
      if (p is JViewport) {
        val gp = p.getParent()
        if (gp is JScrollPane) {
          val viewport: JViewport = gp.viewport
          if (viewport.view === table) {
            setUp(gp)
            previousTableViewport = p
          }
        }
      }
    }
  }

  /** Sets up the header, placing it on a new viewport for the given Scrollpane. */
  private fun setUp(scrollPane: JScrollPane) {
    headerViewport = object : JViewport() {
      private val serialVersionUID = 7109623726722227105L

      init {
        isOpaque = false
      }

      override fun setView(view: Component?) {
        // if the view is not a table header, somebody is doing
        // funny stuff. not much to do!
        if (view is JTableHeader) {
          removeTableHeader()
          // the view is always added, even if set non-visible
          // this way, it can be recovered if the position changes
          view.setVisible(myLocation != AdditionalTableHeader.Position.REPLACE)
          view.setOpaque(false)
          statisticsHeader.add(view, if (myLocation == AdditionalTableHeader.Position.INLINE) BorderLayout.NORTH else BorderLayout.SOUTH)
          statisticsHeader.revalidate()
          super.setView(statisticsHeader)
        }
        else if (view is AdditionalTableHeader) {
          setView(view.findTableHeader())
        }
      }

      /**
       * Removes the current JTableHeader in the filterHeader, returning
       * it. it does nothing if there is no such JTableHeader
       */
      private fun removeTableHeader(): Component? {
        val tableHeader = statisticsHeader.findTableHeader()
        if (tableHeader != null) {
          statisticsHeader.remove(tableHeader)
        }
        return tableHeader
      }
    }
    val currentColumnHeader = scrollPane.columnHeader
    if (currentColumnHeader != null) {
      // this happens if the table has not been yet added to the
      // scrollPane
      val view = currentColumnHeader.view
      if (view != null) {
        headerViewport!!.setView(view)
      }
    }
    scrollPane.setColumnHeader(headerViewport)
  }

  /** Removes the current viewport, setting it up to clean status.  */
  private fun cleanUp() {
    val currentViewport = headerViewport
    headerViewport = null
    if (currentViewport != null) {
      currentViewport.remove(statisticsHeader)
      val parent = currentViewport.parent
      if (parent is JScrollPane) {
        if (parent.columnHeader === currentViewport) {
          val tableHeader = statisticsHeader.findTableHeader()
          val newView = tableHeader?.let { createCleanViewport(it) }
          parent.setColumnHeader(newView)
        }
      }
    }
  }

  /** Creates a simple JViewport with the given component as view.  */
  private fun createCleanViewport(tableHeader: Component): JViewport {
    val ret = JViewport()
    ret.setView(tableHeader)
    return ret
  }

  /** Returns the JTableHeader in the filterHeader, if any.  */
  private fun AdditionalTableHeader.findTableHeader(): Component? {
    for (component in components) {
      // there should be just one (the header's controller)
      // or two components (with the table header)
      if (component is JTableHeader) {
        return component
      }
    }
    return null
  }
}