// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.elevation.settings

import com.intellij.execution.process.elevation.ElevationBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.StartupUiUtil
import java.awt.FontMetrics

internal object ExplanatoryTextUiUtil {
  fun message(@NlsContexts.DialogMessage firstSentence: String,
              maxLineLength: Int = 70,
              fontMetrics: FontMetrics? = null): @NlsSafe String {
    return messageHtmlInnards(firstSentence)
      .limitWidth(maxLineLength, fontMetrics)
      .wrapWith(HtmlChunk.body())
      .wrapWith(HtmlChunk.html())
      .toString()
  }

  private fun messageHtmlInnards(@NlsContexts.DialogMessage firstSentence: String): HtmlChunk {
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    val commentHtml = ElevationBundle.message("text.elevation.explanatory.comment.html", productName)
    val warningHtml = ElevationBundle.message("text.elevation.explanatory.warning.html")

    return HtmlBuilder()
      .append(HtmlChunk.p().addText(firstSentence)).br()
      .append(HtmlChunk.p().addRaw(commentHtml)).br()
      .append(HtmlChunk.p().addRaw(warningHtml)).br()
      .toFragment()
  }

  private fun HtmlChunk.limitWidth(maxLineLength: Int,
                                   fontMetrics: FontMetrics?): HtmlChunk {
    if (maxLineLength <= 0) return this
    val maxWidth = stringWidth(toString(), maxLineLength, fontMetrics)
    return wrapWith(HtmlChunk.div().attr("width", maxWidth))
  }

  private fun stringWidth(someText: String,
                          maxLineLength: Int,
                          fontMetrics: FontMetrics?): Int {
    val text = someText.ifEmpty { "some text to estimate string width with given metric" }
    val substring = text.repeat((maxLineLength + 1) / (text.length + 1) + 1).substring(0, maxLineLength)
    return fontMetrics?.stringWidth(substring) ?: GraphicsUtil.stringWidth(substring, StartupUiUtil.getLabelFont())
  }
}

