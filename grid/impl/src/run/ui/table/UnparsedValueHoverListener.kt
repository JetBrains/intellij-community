package com.intellij.database.run.ui.table

import com.intellij.database.DataGridBundle
import com.intellij.database.run.ui.grid.editors.UnparsedValue
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.TableHoverListener
import com.intellij.util.Alarm
import com.intellij.util.ui.UIUtil
import javax.swing.JScrollPane
import javax.swing.JTable

class UnparsedValueHoverListener(private val place: Place, disposable: Disposable) : TableHoverListener() {
  private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, disposable)
  private var balloonRow: Int? = null
  private var balloonColumn: Int? = null
  private var balloon: Balloon? = null

  override fun onHover(table: JTable, row: Int, column: Int) {
    if (row == -1 || column == -1) return
    val value = table.getValueAt(row, column)
    alarm.cancelAllRequests()
    if (value !is UnparsedValue) {
      hideTooltip()
    }
    else showTooltip(table, row, column, value)
  }

  private fun showTooltip(table: JTable, row: Int, column: Int, value: UnparsedValue) {
    if (balloonRow == row && balloonColumn == column) return
    hideTooltip()
    alarm.addRequest(
      {
        val message = value.error?.message ?: DataGridBundle.message("popup.content.unparsed.value")
        balloon = JBPopupFactory.getInstance()
          .createHtmlTextBalloonBuilder(message, MessageType.ERROR, null)
          .setHideOnAction(true)
          .setHideOnClickOutside(true)
          .setHideOnLinkClick(true)
          .createBalloon()
        balloonRow = row
        balloonColumn = column

        val cellRect = table.getCellRect(row, column, true)
        val scrollPane = UIUtil.getParentOfType(JScrollPane::class.java, table)
        val point = cellRect.location.apply {
          when (place) {
            Place.CENTER -> translate(cellRect.width / 2, cellRect.height)
            Place.LEFT -> translate(0, cellRect.height)
          }
          move(maxOf(x, scrollPane?.horizontalScrollBar?.value ?: 0), y)
        }
        balloon?.show(RelativePoint(table, point), Balloon.Position.below)
      }, 300)
  }

  private fun hideTooltip() {
    balloon?.hide()
    balloon = null
    balloonRow = null
    balloonColumn = null
  }

  companion object {
    enum class Place {
      LEFT,
      CENTER
    }
  }
}