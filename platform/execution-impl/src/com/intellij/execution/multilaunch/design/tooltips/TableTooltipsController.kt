package com.intellij.execution.multilaunch.design.tooltips

import com.intellij.execution.multilaunch.design.Debouncer
import com.intellij.execution.multilaunch.design.extensions.isOver
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.hover.HoverListener
import com.intellij.util.ui.Html
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.Lifetime
import java.awt.Component
import java.awt.Point
import javax.swing.JTable
import javax.swing.SwingUtilities

class TableTooltipsController(lifetime: Lifetime, private val schedulerKey: Any = TableTooltipsController::class.java) : HoverListener() {
  private var tooltip: Balloon? = null
  private val tooltipScheduler = Debouncer(Registry.intValue("ide.tooltip.initialReshowDelay").toLong(), lifetime)
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
          val shadowEnabled = Registry.`is`("ide.balloon.shadowEnabled")
          val shadowSize = Registry.intValue("ide.balloon.shadow.size")
          val newTooltip = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
              Html(provider.tooltipText).setKeepFont(true),
              null,
              UIUtil.getToolTipForeground(),
              UIUtil.getToolTipBackground(),
              null)
            .setShadow(shadowEnabled)
            .setAnimationCycle(0)
            .setBorderColor(JBUI.CurrentTheme.Tooltip.borderColor())
            .setShowCallout(false)
            .createBalloon()
          val tooltipSize = newTooltip.preferredSize
          val shadowOffset = when (shadowEnabled) {
            true -> Point(JBUI.scale(shadowSize), JBUI.scale(shadowSize))
            false -> Point(0, 0)
          }
          val balloonOffset = Point(JBUI.scale(10), JBUI.scale(10))
          val tooltipLocation = RelativePoint(table, Point(
            tableMouse.x + tooltipSize.width / 2 - shadowOffset.x + balloonOffset.x,
            tableMouse.y + tooltipSize.height / 2 - shadowOffset.y + balloonOffset.y
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