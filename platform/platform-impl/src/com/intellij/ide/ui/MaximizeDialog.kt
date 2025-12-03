// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScreenUtil
import java.awt.Rectangle
import javax.swing.JDialog
import kotlin.math.abs

/**
 * Attempts to toggle the maximized state of the dialog.
 *
 * If the dialog is currently not maximized, then it's maximized.
 *
 * If the dialog was maximized using this function or [maximize], then it's normalized.
 *
 * Note that if the dialog was maximized using some other means, it's impossible to normalize it this way,
 * because the "normal, not maximized" size and location are unknown then,
 * as they're stored internally.
 */
fun JDialog.toggleMaximized() {
  if (canBeMaximized()) {
    maximize()
  }
  else if (canBeNormalized()) {
    normalize()
  }
}

/**
 * Checks if the dialog can be maximized.
 *
 * It's possible to maximize a dialog if it's resizable, showing, has a root pane and is not currently maximized.
 *
 * If this function returns `true`, then the dialog can be maximized using [maximize] or [toggleMaximized].
 */
fun JDialog.canBeMaximized(): Boolean {
  if (!commonResizingConditionsAreMet()) return false
  return !almostEquals(ScreenUtil.getScreenRectangle(this), bounds)
}

/**
 * Maximizes the dialog.
 *
 * Because Swing dialogs are not normally maximizable, this function imitates maximizing by simply resizing the dialog to fill the screen.
 *
 * The dialog will still look as not maximized according to its window decorations, but it's the best that can be done due to technical limitations.
 *
 * See [canBeMaximized] for the exact conditions when a dialog can be maximized.
 */
fun JDialog.maximize() {
  if (!canBeMaximized()) return
  ClientProperty.put(this, NORMAL_BOUNDS, bounds)
  bounds = ScreenUtil.getScreenRectangle(this)
}

/**
 * Checks if the dialog can be normalized.
 *
 * It's possible to normalize a dialog if it's resizable, showing, has a root pane, was previously maximized using [maximize] or [toggleMaximized]
 * and it's still maximized.
 *
 * Note that if the dialog was maximized using some other means, it's impossible to normalize it this way,
 * because the "normal, not maximized" size and location are unknown then,
 * as they're stored internally.
 *
 * If this function returns `true`, then the dialog can be normalized using [normalize] or [toggleMaximized].
 */
fun JDialog.canBeNormalized(): Boolean {
  if (!commonResizingConditionsAreMet()) return false
  val screenRectangle = ScreenUtil.getScreenRectangle(this)
  return almostEquals(bounds, screenRectangle) &&
         ClientProperty.get(this, NORMAL_BOUNDS) != null
}

/**
 * Normalizes the dialog.
 *
 * The dialog's size and position will be restored to the values that were stored when
 * [maximize] or [toggleMaximized] was called to maximize the dialog.
 * If those bounds aren't within a screen, they will be made to fit the screen
 * (in case the screen configuration has changed, for example).
 *
 * See [canBeNormalized] for the exact conditions when it's possible to normalize a dialog.
 */
fun JDialog.normalize() {
  if (!canBeNormalized()) return
  val normalBounds = ClientProperty.get(this, NORMAL_BOUNDS)
  if (normalBounds != null) {
    ScreenUtil.fitToScreen(normalBounds)
    bounds = normalBounds
    ClientProperty.remove(this, NORMAL_BOUNDS)
  }
}

private fun JDialog.commonResizingConditionsAreMet(): Boolean =
  isShowing && // needed for getScreenRectangle
  isResizable && // can't resize if it's not resizable to begin with
  rootPane != null // needed to store the client property

private fun almostEquals(r1: Rectangle, r2: Rectangle): Boolean {
  val tolerance = Registry.intValue("ide.dialog.maximize.tolerance", 10)
  return abs(r1.x - r2.x) <= tolerance &&
         abs(r1.y - r2.y) <= tolerance &&
         abs(r1.width - r2.width) <= tolerance &&
         abs(r1.height - r2.height) <= tolerance
}

private val NORMAL_BOUNDS = Key.create<Rectangle>("NORMAL_BOUNDS")
