/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.notebooks.visualization.r.inlays

import com.intellij.util.ui.JBUI

object InlayDimensions {

  /**
   * Offset for inlay painted round-rect background.
   * We need it to draw visual offsets from surrounding text.
   */
  private const val bottomOffsetUnscaled = 24
  private const val bottomBorderUnscaled = bottomOffsetUnscaled + 5

  /** Real borders for inner inlay component */
  val bottomBorder = JBUI.scale(bottomBorderUnscaled)
}