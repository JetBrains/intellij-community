// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.NonNls
import java.awt.Container
import java.awt.Font
import java.awt.FontMetrics
import java.awt.LayoutManager
import java.util.*
import java.util.function.Supplier

internal class ClippingLayoutWrapper(private val delegate: LayoutManager): LayoutManager by delegate {

  override fun layoutContainer(parent: Container) {
    val preferredSize = preferredLayoutSize(parent)
    if (parent.width < preferredSize.width) tryClipping(parent)

    delegate.layoutContainer(parent)
  }

  private fun tryClipping(parent: Container) {
    var fixedWidth = 0
    val elementsToClip = mutableListOf<SimpleColoredComponent>()
    for (cmp in parent.components) {
      val scc = cmp as? SimpleColoredComponent ?: continue
      if (canClip(scc)) elementsToClip.add(scc)
      else fixedWidth += scc.preferredSize.width
    }

    if (elementsToClip.isEmpty()) return

    val childMaxWith = (parent.width - fixedWidth).coerceAtLeast(0) / elementsToClip.size
    elementsToClip.forEach { clip(it, childMaxWith) }
  }

  private fun canClip(scc: SimpleColoredComponent): Boolean {
    val iterator = scc.iterator()

    //while we have just one append we can rely on fact that iterator have exactly one item
    iterator.next()
    val text = iterator.fragment
    return text.count { it == '/' } > 1
  }

  private fun clip(component: SimpleColoredComponent, maxWidth: Int) {
    val iterator = component.iterator()

    //while we have just one append we can rely on fact that iterator have exactly one item
    iterator.next()
    val text = iterator.fragment
    val attributes = iterator.textAttributes

    val font = Optional.ofNullable<Font>(component.font).orElseGet(Supplier<Font> { StartupUiUtil.labelFont })
    val fm = component.getFontMetrics(font)

    val textWidth = fm.stringWidth(text)
    val compWidth = component.preferredSize.width
    val adjustedMaxWidth = maxWidth - (compWidth - textWidth)
    @NonNls val shortName = shortenPath(text, fm, adjustedMaxWidth)
    component.clear()
    component.append(shortName, attributes)
  }


  private fun shortenPath(path: String, fm: FontMetrics, maxWidth: Int): String {
    val separator = "/"
    val ellipsis = "..."
    val parts = path.split(separator).toMutableList()

    while (parts.isNotEmpty() && fm.stringWidth(separator + parts.joinToString(separator) + separator + ellipsis) > maxWidth) {
      val middle = parts.size / 2
      parts.removeAt(middle)
    }

    return if (parts.isNotEmpty()) {
      val middle = parts.size / 2
      parts.add(middle, ellipsis)
      parts.joinToString(separator)
    } else {
      // Path is too small to display, just show ellipsis
      ellipsis
    }
  }

}