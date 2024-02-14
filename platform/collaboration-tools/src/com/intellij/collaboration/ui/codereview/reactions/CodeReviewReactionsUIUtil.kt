// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.reactions

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.Nls

object CodeReviewReactionsUIUtil {
  const val BUTTON_HEIGHT: Int = 22
  const val BUTTON_ROUNDNESS: Int = 24

  const val HORIZONTAL_GAP: Int = 8

  const val ICON_SIZE: Int = 16

  object Picker {
    const val WIDTH: Int = 358
    const val HEIGHT: Int = 415

    const val BLOCK_PADDING: Int = 5

    const val EMOJI_HEIGHT: Int = 40
    const val EMOJI_WIDTH: Int = 40
    const val EMOJI_ICON_SIZE: Int = 24

    const val BUTTON_PADDING: Int = 4
    const val BUTTON_HEIGHT: Int = 24
    const val BUTTON_WIDTH: Int = 32
  }

  fun createTooltipText(users: List<String>, reactionName: String): @Nls String {
    val reactors = users.chunked(3).joinToString(HtmlChunk.br().toString()) { chunk ->
      chunk.joinToString(", ") { reactorName: @NlsSafe String ->
        HtmlChunk.text(reactorName).bold().toString()
      }
    } + HtmlChunk.br().toString()
    return HtmlBuilder()
      .appendRaw(CollaborationToolsBundle.message("review.comments.reaction.tooltip", reactors, reactionName))
      .wrapWith(HtmlChunk.div("text-align: center"))
      .wrapWith(HtmlChunk.body())
      .wrapWith(HtmlChunk.html())
      .toString()
  }
}