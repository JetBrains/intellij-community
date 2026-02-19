package com.intellij.execution.multilaunch.design.popups

import com.intellij.execution.multilaunch.design.extensions.isOver
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JTable
import javax.swing.SwingUtilities

class TableSelectorPopupController : MouseAdapter() {
  private val selectorPopupProvidersTracker = SelectorPopupProvidersTracker()

  fun install(table: JTable) {
    table.addMouseListener(this)
    table.addContainerListener(selectorPopupProvidersTracker)
  }

  fun uninstall(table: JTable) {
    table.removeMouseListener(this)
    table.removeContainerListener(selectorPopupProvidersTracker)
  }

  override fun mouseClicked(e: MouseEvent?) {
    val event = e ?: return
    if (!SwingUtilities.isLeftMouseButton(event)) return

    val table = e.component as? JTable ?: return
    val mouseX = event.x
    val mouseY = event.y
    val tableMouse = Point(mouseX, mouseY)

    val provider = selectorPopupProvidersTracker.providers.firstOrNull {
      val regionComponent = it.selectorTarget
      val localMouse = SwingUtilities.convertPoint(table, tableMouse, regionComponent)
      val localRegion = SwingUtilities.convertRectangle(regionComponent.parent, regionComponent.bounds, regionComponent)
      localMouse isOver localRegion
    }

    provider?.invokeSelectionPopup()
  }
}