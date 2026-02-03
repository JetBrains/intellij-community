// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScreenUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Rectangle
import javax.swing.JDialog
import kotlin.math.abs

/**
 * Determines whether the given dialog is maximizable.
 *
 * Unlike [canBeMaximized], which checks whether the dialog can be maximized right now,
 * this property determines whether the maximize/restore functionality should be available for the given dialog at all.
 *
 * By default, dialogs are not maximizable, because they're not designed to be maximizable.
 * If they're maximized, e.g., by accidentally double-clicking on the header, they just don't look right.
 *
 * In the current implementation, this property only affects behavior on Windows.
 * On macOS, all dialogs are maximizable by the OS.
 * On Linux, dialogs have native headers, and it's the environment that determines whether they can be maximized (usually they can not).
 */
var JDialog.isMaximizable: Boolean
  get() = ClientProperty.isTrue(this, MAXIMIZABLE)
  set(value) {
    ClientProperty.put(this, MAXIMIZABLE, if (value) true else null)
  }

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
 *
 * @see [isMaximizable]
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
 * Checks if the dialog can be maximized right now.
 *
 * It's possible to maximize a dialog if it's resizable, showing, has a root pane and is not currently maximized.
 *
 * If this function returns `true`, then the dialog can be maximized using [maximize] or [toggleMaximized].
 *
 * @see [isMaximizable]
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
 *
 * @see [isMaximizable]
 */
fun JDialog.maximize() {
  if (!canBeMaximized()) return
  val screenRectangle = ScreenUtil.getScreenRectangle(this)
  val bounds = this.bounds
  // A special case: the dialog has already the maximum size, but its location is off.
  // This means it can be "maximized" (moved to fit the screen), but we should not store the current bounds, for two reasons:
  // 1. It won't be possible to normalize it anyway because of the fit-to-screen logic.
  // 2. If the dialog was previously maximized, it already has sensible normal bounds stored, and we want to keep them.
  if (!almostHaveTheSameSize(bounds, screenRectangle)) {
    this.normalBounds = bounds
  }
  this.bounds = screenRectangle
}

/**
 * Checks if the dialog can be normalized right now.
 *
 * It's possible to normalize a dialog if it's resizable, showing, has a root pane, was previously maximized using [maximize] or [toggleMaximized]
 * and it's still maximized.
 *
 * Note that if the dialog was maximized using some other means, it's impossible to normalize it this way,
 * because the "normal, not maximized" size and location are unknown then,
 * as they're stored internally.
 *
 * If this function returns `true`, then the dialog can be normalized using [normalize] or [toggleMaximized].
 *
 * @see [isMaximizable]
 */
fun JDialog.canBeNormalized(): Boolean {
  if (!commonResizingConditionsAreMet()) return false
  val screenRectangle = ScreenUtil.getScreenRectangle(this)
  return almostEquals(bounds, screenRectangle) && normalBounds != null
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
 *
 * @see [isMaximizable]
 */
fun JDialog.normalize() {
  if (!canBeNormalized()) return
  val normalBounds = this.normalBounds
  if (normalBounds != null) {
    ScreenUtil.fitToScreen(normalBounds)
    bounds = normalBounds
    this.normalBounds = null
  }
}

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var JDialog.normalBounds: Rectangle?
  get() = ClientProperty.get(this, NORMAL_BOUNDS)
  set(value) {
    ClientProperty.put(this, NORMAL_BOUNDS, value)
  }

private fun JDialog.commonResizingConditionsAreMet(): Boolean =
  isShowing && // needed for getScreenRectangle
  isResizable && // can't resize if it's not resizable to begin with
  isMaximizable && // maximization is enabled by the client
  rootPane != null // needed to store the client property

private fun almostEquals(r1: Rectangle, r2: Rectangle): Boolean {
  val tolerance = Registry.intValue("ide.dialog.maximize.tolerance", 10)
  return abs(r1.x - r2.x) <= tolerance &&
         abs(r1.y - r2.y) <= tolerance &&
         abs(r1.width - r2.width) <= tolerance &&
         abs(r1.height - r2.height) <= tolerance
}

private fun almostHaveTheSameSize(r1: Rectangle, r2: Rectangle): Boolean {
  val tolerance = Registry.intValue("ide.dialog.maximize.tolerance", 10)
  return abs(r1.width - r2.width) <= tolerance && abs(r1.height - r2.height) <= tolerance
}

private val NORMAL_BOUNDS = Key.create<Rectangle>("NORMAL_BOUNDS")
private val MAXIMIZABLE = Key.create<Rectangle>("MAXIMIZABLE")
