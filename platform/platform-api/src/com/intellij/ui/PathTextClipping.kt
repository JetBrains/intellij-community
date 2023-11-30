// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ui.SimpleColoredComponent.FragmentTextClipper
import java.awt.Graphics2D

class PathTextClipping: FragmentTextClipper {

  companion object {
    private val INSTANCE = PathTextClipping()

    @JvmStatic
    fun getInstance(): PathTextClipping = INSTANCE
  }
  override fun clipText(component: SimpleColoredComponent, g2: Graphics2D, fragmentIndex: Int, text: String, availTextWidth: Int): String {
    val fm = component.getFontMetrics(g2.font)
    if (fm.stringWidth(text) <= availTextWidth) return text

    val separator = "/"
    val ellipsis = "..."
    val parts = text.split(separator).toMutableList()

    while (!parts.isEmpty() && fm.stringWidth(java.lang.String.join(separator, parts) + separator + ellipsis) > availTextWidth) {
      parts.removeAt(0)
    }

    if (!parts.isEmpty()) {
      parts.add(0, ellipsis)
      parts.joinToString(separator)
      return java.lang.String.join(separator, parts)
    }
    else {
      // Path is too small to display, just show ellipsis
      return ellipsis
    }

  }
}