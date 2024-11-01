// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.ColorUtil
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.JComponent
import javax.swing.border.Border

object CodeReviewTimelineUIUtil {
  const val VERTICAL_GAP: Int = 4

  const val VERT_PADDING: Int = 6
  const val HEADER_VERT_PADDING: Int = 20

  const val ITEM_HOR_PADDING: Int = 16
  const val ITEM_VERT_PADDING: Int = 10

  val ITEM_BORDER: Border get() = JBUI.Borders.empty(ITEM_HOR_PADDING, ITEM_VERT_PADDING)

  object Thread {
    const val DIFF_TEXT_GAP = 8

    object Replies {
      object ActionsFolded {
        const val VERTICAL_PADDING = 8
        const val HORIZONTAL_GAP = 8
        const val HORIZONTAL_GROUP_GAP = 14
      }
    }
  }

  fun createTitleTextPane(authorName: @Nls String, authorUrl: String?, date: Date?): JComponent {
    val titleText = getTitleHtml(authorName, authorUrl, date)
    val titleTextPane = SimpleHtmlPane(titleText).apply {
      foreground = UIUtil.getContextHelpForeground()
    }
    return titleTextPane
  }

  private fun getTitleHtml(authorName: @Nls String, authorUrl: String?, date: Date?): @NlsSafe String {
    val userNameLink = (authorUrl?.let { HtmlChunk.link(it, authorName) } ?: HtmlChunk.text(authorName))
      .wrapWith(HtmlChunk.font(ColorUtil.toHtmlColor(UIUtil.getLabelForeground())))
      .bold()
    val builder = HtmlBuilder()
      .append(userNameLink)
    if (date != null) {
      builder
        .append(HtmlChunk.nbsp())
        .append(DateFormatUtil.formatPrettyDateTime(date))
    }

    return builder.toString()
  }
}