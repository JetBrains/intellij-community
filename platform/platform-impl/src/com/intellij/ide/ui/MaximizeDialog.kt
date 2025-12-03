// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ScreenUtil
import java.awt.Rectangle
import javax.swing.JDialog
import kotlin.math.abs

fun doMaximize(dialog: JDialog) {
  if (canBeMaximized(dialog)) {
    maximize(dialog)
  }
  else if (canBeNormalized(dialog)) {
    normalize(dialog)
  }
}

fun canBeMaximized(dialog: JDialog?): Boolean {
  val rootPane = if (dialog != null && dialog.isResizable) dialog.getRootPane() else null
  if (rootPane == null) return false
  return !almostEquals(ScreenUtil.getScreenRectangle(dialog!!), dialog.bounds)
}

fun maximize(dialog: JDialog) {
  if (!canBeMaximized(dialog)) return
  dialog.getRootPane().putClientProperty(NORMAL_BOUNDS, dialog.bounds)
  dialog.bounds = ScreenUtil.getScreenRectangle(dialog)
}

fun canBeNormalized(dialog: JDialog?): Boolean {
  val rootPane = if (dialog != null && dialog.isResizable) dialog.getRootPane() else null
  if (rootPane == null) return false
  val screenRectangle = ScreenUtil.getScreenRectangle(dialog!!)
  return almostEquals(dialog.bounds, screenRectangle) &&
         rootPane.getClientProperty(NORMAL_BOUNDS) is Rectangle
}

fun normalize(dialog: JDialog) {
  if (!canBeNormalized(dialog)) return
  val rootPane = dialog.getRootPane()
  val value = rootPane.getClientProperty(NORMAL_BOUNDS)
  if (value is Rectangle) {
    ScreenUtil.fitToScreen(value)
    dialog.bounds = value
    rootPane.putClientProperty(NORMAL_BOUNDS, null)
  }
}

private fun almostEquals(r1: Rectangle, r2: Rectangle): Boolean {
  val tolerance = Registry.intValue("ide.dialog.maximize.tolerance", 10)
  return abs(r1.x - r2.x) <= tolerance &&
         abs(r1.y - r2.y) <= tolerance &&
         abs(r1.width - r2.width) <= tolerance &&
         abs(r1.height - r2.height) <= tolerance
}

private const val NORMAL_BOUNDS = "NORMAL_BOUNDS"
