// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ui.tabs.impl

import com.intellij.ui.tabs.JBTabsBorder
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*

@Internal
open class JBDefaultTabsBorder(tabs: JBTabsImpl) : JBTabsBorder(tabs) {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (tabs.isEmptyVisible) {
      return
    }

    g as Graphics2D

    val rect = Rectangle(x, y, width, height)
    val firstLabel = tabs.getTabLabel(tabs.getVisibleInfos().first()) ?: return
    val maxY = firstLabel.bounds.maxY.toInt() - thickness
    tabs.tabPainter.paintBorderLine(g, thickness, Point(rect.x, maxY), Point(rect.maxX.toInt(), maxY))
  }
}