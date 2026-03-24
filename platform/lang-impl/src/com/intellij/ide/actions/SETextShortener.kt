// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.system.OS
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
      OS.CURRENT == OS.Windows && text.contains("\\") -> "\\"
      text.contains(".") -> "."
      text.contains("-") -> "-"
      else -> " "
    }

    val dotsShortening = if (separator != ".") "..." else ".."
    val parts = LinkedList(StringUtil.split(text, separator))
    var index: Int
    while (parts.size > 1) {
      index = parts.size / 2 - 1
      parts.removeAt(index)
      if (getTextWidth(left + StringUtil.join(parts, separator) + dotsShortening + separator) < maxWidth) {
        parts.add(index, dotsShortening)
        return left + StringUtil.join(parts, separator)
      }
    }
    val adjustedWidth = max(adjustedText.length * maxWidth / fullWidth - 1,left.length + 3)
    return StringUtil.trimMiddle(adjustedText, adjustedWidth)
  }

  /**
   * Shortens the given text to fit within the specified maximum width.
   *
   * @param originalText a text to shorten
   * @param maxWidth the maximum allowed width in pixels or other metrics
   * @param getTextWidth a function which calculates text width in pixels or other metrics
   * @return the shortened text
   */
  fun getShortenText(originalText: @NlsSafe String, maxWidth: Int, getTextWidth: (String) -> Int): @NlsSafe String {
    if (maxWidth <= 0) return originalText

    val length = originalText.length
    if (length == 0) return originalText

    val textWidth = getTextWidth(originalText)
    if (textWidth == 0 || textWidth <= maxWidth) return originalText

    // Approximate new length
    var newLength = maxWidth * length / textWidth
    if (newLength == 0) return originalText

    var newText = originalText.substring(0, newLength)
    var newWidth = getTextWidth(newText)

    // If the text became shorter than max,
    // then we add length until it becomes longer than max and return the prefix of the new length - 1
    while (newWidth < maxWidth) {
      newLength += 1
      newWidth = getTextWidth(originalText.substring(0, newLength))

      if (newWidth >= maxWidth) {
        return originalText.substring(0, newLength - 1)
      }
    }

    // If the text became longer than max,
    // then we subtract length until it becomes shorter than max and return the prefix of the new length
    while (newWidth > maxWidth) {
      newLength -= 1
      newText = originalText.substring(0, newLength)
      newWidth = getTextWidth(newText)

      if (newWidth <= maxWidth) return newText
    }

    return newText
  }
}