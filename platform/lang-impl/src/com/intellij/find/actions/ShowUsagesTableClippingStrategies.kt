// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleColoredComponent
import com.intellij.usages.UsageGroup
import com.intellij.util.ui.StartupUiUtil.labelFont
import java.awt.Font
import java.awt.FontMetrics
import java.util.*
import java.util.function.Supplier

internal interface ClippingStrategy {
  fun cutText(component: SimpleColoredComponent, maxWidth: Int)
}

internal fun getClippingStrategy(type: UsageGroup.ClippingMode): ClippingStrategy = when(type) {
  UsageGroup.ClippingMode.NAME_CLIPPING -> NameClippingStrategy
  UsageGroup.ClippingMode.PATH_CLIPPING -> PathClippingStrategy
  else -> NoClippingStrategy
}

private object NameClippingStrategy : ClippingStrategy {
  override fun cutText(component: SimpleColoredComponent, maxWidth: Int) = cutComponentContent(component, maxWidth, ::shortenName)
}

private object PathClippingStrategy : ClippingStrategy {
  override fun cutText(component: SimpleColoredComponent, maxWidth: Int) = cutComponentContent(component, maxWidth, ::shortenPath)
}

private object NoClippingStrategy : ClippingStrategy {
  override fun cutText(component: SimpleColoredComponent, maxWidth: Int) {}
}

private fun shortenName(originalName: String, fm: FontMetrics, maxWidth: Int): String {
  var text = originalName
  val currentWidth = fm.stringWidth(text)  // get current width of the text
  if (currentWidth <= maxWidth) return text

  val ratio = maxWidth.toDouble() / currentWidth.toDouble()
  val estimatedLength = (text.length * ratio).toInt() - 3
  var cutLength = (text.length - estimatedLength) / 2
  val middle = text.length / 2
  text = text.substring(0, middle - cutLength) + "..." + text.substring(middle + cutLength)

  // Further truncate the text if it's still wider than maxWidth
  while (fm.stringWidth(text) > maxWidth){
    text = text.substring(0, middle - cutLength) + "..." + text.substring(middle + cutLength)
    cutLength++
  }

  return text
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

private fun cutComponentContent(component: SimpleColoredComponent, maxWidth: Int, textShortener: (String, FontMetrics, Int) -> @NlsSafe String) {
  val iterator = component.iterator()

  //while we have just one append we can rely on fact that iterator have exactly one item
  iterator.next()
  val text = iterator.fragment
  val attributes = iterator.textAttributes

  val font = Optional.ofNullable<Font>(component.font).orElseGet(Supplier<Font> { labelFont })
  val fm = component.getFontMetrics(font)

  val textWidth = fm.stringWidth(text)
  val compWidth = component.preferredSize.width
  val adjustedMaxWidth = maxWidth - (compWidth - textWidth)
  val shortName = textShortener(text, fm, adjustedMaxWidth)
  component.clear()
  component.append(shortName, attributes)
}
