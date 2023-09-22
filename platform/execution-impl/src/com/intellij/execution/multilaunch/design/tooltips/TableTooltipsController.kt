package com.intellij.execution.multilaunch.design.tooltips

import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.Html
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.execution.multilaunch.design.Debouncer
import com.intellij.execution.multilaunch.design.extensions.isOver
import java.awt.Component
import java.awt.Point
import java.util.concurrent.TimeUnit
import javax.swing.JTable
import javax.swing.SwingUtilities

class TableTooltipsController(private val schedulerKey: Any = TableTooltipsController::class.java) : HoverListener() {
  private var tooltip: Balloon? = null
  private val tooltipScheduler = Debouncer(500, TimeUnit.MILLISECONDS)
  private val tooltipProvidersTracker = TooltipProvidersTracker()

  fun install(table: JTable) {
    addTo(table)
    table.addContainerListener(tooltipProvidersTracker)
  }

  fun uninstall(table: JTable) {
    removeFrom(table)
    table.removeContainerListener(tooltipProvidersTracker)
  }

  override fun mouseEntered(component: Component, x: Int, y: Int) {}

  override fun mouseMoved(table: Component, x: Int, y: Int) {
    tooltip?.hideImmediately()
    val tableMouse = Point(x, y)
    val provider = tooltipProvidersTracker.providers.firstOrNull {
      val regionComponent = it.tooltipTarget
      val localMouse = SwingUtilities.convertPoint(table, tableMouse, regionComponent)
      val localRegion = SwingUtilities.convertRectangle(regionComponent.parent, regionComponent.bounds, regionComponent)
      localMouse isOver localRegion
    }

    when (provider) {
      null -> {
        // We don't hover any editor components, cancel tooltip
        tooltipScheduler.cancel(schedulerKey)
      }
      else -> {
        tooltipScheduler.call(schedulerKey) {
          val newTooltip = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(Html(provider.tooltipText).setKeepFont(true), null, UIUtil.getLabelTextForeground(),
                                          UIUtil.getPanelBackground(), null)
            .setShadow(false)
            .setShowCallout(false)
            .setAnimationCycle(100)
            .setBorderColor(JBColor.border())
            .createBalloon()
          val tooltipSize = newTooltip.preferredSize
          val tooltipLocation = RelativePoint(table, Point(
            tableMouse.x + tooltipSize.width / 2 + JBUI.scale(10),
            tableMouse.y + tooltipSize.height / 2 + JBUI.scale(10)
          ))
          newTooltip.show(tooltipLocation, Balloon.Position.below)
          tooltip = newTooltip
        }
      }
    }
  }

  override fun mouseExited(component: Component) {
    tooltipScheduler.cancel(schedulerKey)
  }
}