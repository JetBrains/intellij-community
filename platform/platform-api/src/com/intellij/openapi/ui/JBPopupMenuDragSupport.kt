@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui

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

private var currentMenu: WeakReference<JBPopupMenu?> = WeakReference(null)

private val SESSION_KEY = Key.create<MenuDragSession>("MenuDragSession")

internal fun prepareForMenuDragSession(menu: JBPopupMenu, invoker: Component?, x: Int, y: Int) {
  if (
    SystemInfoRt.isWindows || // On Windows click-by-drag is neither supported nor desired.
    GraphicsEnvironment.isHeadless() || // Obviously...
    threshold() == 0 || // The feature is disabled.
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
  ClientProperty.put(menu, SESSION_KEY, MenuDragSession(screenPoint.x, screenPoint.y))
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
) {
  private var maximumDragDistance: Int? = null

  fun onMenuDragged(e: MenuDragMouseEvent) {
    val location = e.locationOnScreen
    val distance = max(abs(location.x - initialX), abs(location.y - initialY))
    maximumDragDistance = maximumDragDistance?.let { max(it, distance) } ?: distance
  }

  @Suppress("RedundantIf")
  fun isClickOrNoticeableDrag(): Boolean {
    val distance = maximumDragDistance
    if (distance == null) return true // click
    return distance >= threshold()
  }

}

private fun threshold(): Int {
  val unscaled = if (SystemInfoRt.isMac) {
    Registry.intValue(key = "popup.menu.drag.distance.threshold.mac", defaultValue = 0, minValue = 0, maxValue = 100)
  }
  else { // Linux, because on Windows menus can't be activated this way.
    Registry.intValue(key = "popup.menu.drag.distance.threshold.linux", defaultValue = 0, minValue = 0, maxValue = 100)
  }
  return JBUI.scale(unscaled)
}
