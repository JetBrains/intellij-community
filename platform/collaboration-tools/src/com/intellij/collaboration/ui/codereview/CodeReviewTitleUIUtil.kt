// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.NamedColorUtil
import org.jetbrains.annotations.Nls

object CodeReviewTitleUIUtil {
  fun createTitleText(title: @NlsSafe String, reviewNumber: @NlsSafe String, url: @NlsSafe String, tooltip: @Nls String): @NlsSafe String {
    val reviewNumberLink = HtmlChunk
      .link(url, reviewNumber)
      .attr("title", tooltip)
      .wrapWith(HtmlChunk.font(ColorUtil.toHex(NamedColorUtil.getInactiveTextColor())))

    return HtmlBuilder()
      .appendRaw(title.unwrap())
      .nbsp()
      .append(reviewNumberLink)
      .toString()
  }

  private fun String.unwrap(): @NlsSafe String {
    return removePrefix("<body>").removeSuffix("</body>")
      .removePrefix("<p>").removeSuffix("</p>")
  }
}