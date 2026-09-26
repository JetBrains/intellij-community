@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.lang.ref.WeakReference
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import javax.swing.event.MenuDragMouseEvent
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private var currentMenu: WeakReference<JBPopupMenu?> = WeakReference(null)

private val SESSION_KEY = Key.create<MenuDragSession>("MenuDragSession")

internal fun prepareForMenuDragSession(menu: JBPopupMenu, invoker: Component?, x: Int, y: Int) {
  if (
    SystemInfoRt.isWindows || // On Windows click-by-drag is neither supported nor desired (except LMB drag for the main menu, but it needs no tracking).
    GraphicsEnvironment.isHeadless() || // Obviously...
    distanceThreshold() == 0 || // The feature is disabled.
    MenuSelectionManager.defaultManager().selectedPath.isNotEmpty() || // This is a nested menu, not invoked by right click.
    invoker == null || // Some weird exotic invocation, skip it.
    !invoker.isShowing // Ditto, some out-of-sync invocation or something.
  ) {
    return
  }
  currentMenu = WeakReference(menu)
  startDragSession(menu, invoker, x, y)
}

private fun startDragSession(menu: JBPopupMenu, invoker: Component, x: Int, y: Int) {
  val screenPoint = Point(x, y)
  SwingUtilities.convertPointToScreen(screenPoint, invoker)
  LOG.debug { "Starting a context menu drag session from $screenPoint" }
  ClientProperty.put(menu, SESSION_KEY, MenuDragSession(screenPoint.x, screenPoint.y, TimeSource.Monotonic.markNow()))
}

fun getCurrentMenuDragSession(): MenuDragSession? {
  val currentMenu = currentMenu.get()
  if (currentMenu == null || !currentMenu.isShowing) return null
  return ClientProperty.get(currentMenu, SESSION_KEY)
}

@ApiStatus.Internal
class MenuDragSession internal constructor(
  val initialX: Int,
  val initialY: Int,
  val initialTime: TimeSource.Monotonic.ValueTimeMark,
) {
  private var maximumDragDistance: Int? = null

  fun onMenuDragged(e: MenuDragMouseEvent) {
    val location = e.locationOnScreen
    val distance = max(abs(location.x - initialX), abs(location.y - initialY))
    maximumDragDistance = maximumDragDistance?.let { max(it, distance) } ?: distance
    LOG.trace { "The drag distance is updated: new location = $location, distance = $distance, max distance = $maximumDragDistance" }
  }

  fun isClickOrNoticeableDrag(): Boolean {
    val distance = maximumDragDistance
    if (distance == null) return true // click
    val time = initialTime.elapsedNow()
    val distanceThreshold = distanceThreshold()
    val timeThreshold = timeThreshold()
    LOG.trace {
      "Checking if noticeable: " +
      "distance threshold = $distanceThreshold, " +
      "distance = $distance, " +
      "time threshold = $timeThreshold, " +
      "time = $time"
    }
    val isNoticeable = distance >= distanceThreshold && time >= timeThreshold
    if (isNoticeable) {
      LOG.debug {
        "A noticeable drag is detected, the menu item will be clicked: " +
        "distance threshold = $distanceThreshold, " +
        "distance = $distance, " +
        "time threshold = $timeThreshold, " +
        "time = $time"
      }
    }
    return isNoticeable
  }

}

private fun distanceThreshold(): Int {
  val unscaled = if (SystemInfoRt.isMac) {
    Registry.intValue(key = "popup.menu.drag.distance.threshold.mac", defaultValue = 0, minValue = 0, maxValue = 100)
  }
  else { // Linux, because on Windows menus can't be activated this way.
    Registry.intValue(key = "popup.menu.drag.distance.threshold.linux", defaultValue = 0, minValue = 0, maxValue = 100)
  }
  return JBUI.scale(unscaled)
}

private fun timeThreshold(): Duration {
  val ms = Registry.intValue(key = "popup.menu.drag.time.threshold", defaultValue = 0, minValue = 0, maxValue = 10000)
  return ms.milliseconds
}

private val LOG = fileLogger()
