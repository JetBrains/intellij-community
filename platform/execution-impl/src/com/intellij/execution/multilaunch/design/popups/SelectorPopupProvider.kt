package com.intellij.execution.multilaunch.design.popups

import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JTable

interface SelectorPopupProvider {
  val selectorTarget: JComponent
  fun invokeSelectionPopup()

  fun getSuggestedCellPopupBounds(table: JTable, row: Int, column: Int): Rectangle {
    val cellBounds = table.getCellRect(row, column, false)
    val tableLocation = table.locationOnScreen

    val cellScreenX = tableLocation.x + cellBounds.x
    val cellScreenY = tableLocation.y + cellBounds.y + cellBounds.height

    return Rectangle(cellScreenX, cellScreenY, cellBounds.width, cellBounds.y)
  }
}