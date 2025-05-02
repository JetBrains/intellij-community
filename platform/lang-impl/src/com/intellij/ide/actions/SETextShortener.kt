// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import java.util.LinkedList
import kotlin.math.max

@ApiStatus.Internal
object SETextShortener {

  /**
   * Shortens the given container text to fit within the specified maximum width.
   *
   * @param containerText a text to shorten
   * @param maxWidth the maximum allowed width in pixels or other metrics
   * @param getTextWidth a function which calculates text width in pixels or other metrics
   * @return the shortened text
   */
  fun getShortenContainerText(containerText: @NlsSafe String, maxWidth: Int, getTextWidth: (String) -> Int): @NlsSafe String {
    var text = containerText
    val textStartsWithIn = text.startsWith("in ")
    if (textStartsWithIn) text = text.substring(3)
    val left = if (textStartsWithIn) "in " else ""
    val adjustedText = left + text
    if (maxWidth < 0) return adjustedText

    val fullWidth = getTextWidth(adjustedText)
    if (fullWidth < maxWidth) return adjustedText

    val separator = when {
      text.contains("/") -> "/"
      SystemInfo.isWindows && text.contains("\\") -> "\\"
      text.contains(".") -> "."
      text.contains("-") -> "-"
      else -> " "
    }

    val parts = LinkedList(StringUtil.split(text, separator))
    var index: Int
    while (parts.size > 1) {
      index = parts.size / 2 - 1
      parts.removeAt(index)
      if (getTextWidth(left + StringUtil.join(parts, separator) + "...") < maxWidth) {
        parts.add(index, "...")
        return left + StringUtil.join(parts, separator)
      }
    }
    val adjustedWidth = max((adjustedText.length * maxWidth / fullWidth - 1).toDouble(), (left.length + 3).toDouble()).toInt()
    return StringUtil.trimMiddle(adjustedText, adjustedWidth)
  }
}