// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.reactions

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextCache
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.font.TextLayout
import javax.swing.Icon

object CodeReviewReactionsUIUtil {
  private val PREFERRED_EMOJI_FONTS: List<String> = listOf(
    "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji"
  )

  internal val EMOJI_FONT: Font? by lazy(::findEmojiFont)

  private fun findEmojiFont(): Font? {
    var found: Pair<Int, Font>? = null
    for (font in GraphicsEnvironment.getLocalGraphicsEnvironment().allFonts) {
      val name = font.name
      var priority = PREFERRED_EMOJI_FONTS.indexOf(name)
      if (priority < 0 && name.contains("emoji", true)) {
        priority = Int.MAX_VALUE
      }
      if (priority >= 0 && (found == null || priority < found.first)) {
        found = priority to font
      }
    }
    return found?.second
  }

  internal const val VARIATION_SELECTOR: String = "\uFE0F"

  const val BUTTON_HEIGHT: Int = 24

  const val HORIZONTAL_GAP: Int = 8

  const val ICON_SIZE: Int = 20
  const val COUNTER_FONT_SIZE: Float = 11f

  object Picker {
    const val WIDTH: Int = 358
    const val HEIGHT: Int = 415

    const val BLOCK_PADDING: Int = 5
  }

  fun createUnicodeEmojiIcon(text: String, size: Int): Icon = UnicodeEmojiIcon(text, size)

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

/**
 * Similar in principle to [com.intellij.ui.TextIcon], but also always limits the size to [size]
 * Uses label font to draw the emoji by default, but will perform font fallback lookup if necessary
 */
private class UnicodeEmojiIcon(text: String, private val size: Int) : Icon {
  private val text: String = text.let {
    if (!it.endsWith(CodeReviewReactionsUIUtil.VARIATION_SELECTOR)) {
      it + CodeReviewReactionsUIUtil.VARIATION_SELECTOR
    }
    else {
      it
    }
  }

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    if (c == null) return
    val paintData = getPaintData()
    val g2d = g.create(x, y, paintData.size, paintData.size) as Graphics2D
    try {
      GraphicsUtil.setupAntialiasing(g2d)

      g2d.font = paintData.font
      g2d.color = UIUtil.getLabelForeground()

      val frc = g2d.fontRenderContext
      val layout = TextLayout(text, g2d.font, frc)

      val baselineX = (paintData.size - layout.visibleAdvance).coerceAtLeast(0f) / 2f

      val height = layout.ascent + layout.descent
      val baselineY = layout.ascent + (paintData.size - height).coerceAtLeast(0f) / 2f

      layout.draw(g2d, baselineX, baselineY)
    }
    finally {
      g2d.dispose()
    }
  }

  override fun getIconWidth(): Int = getPaintData().size
  override fun getIconHeight(): Int = getPaintData().size

  private val paintDataCache = ScaleContextCache {
    // we don't use font scale here, bc it's not a text, but icon
    val labelFont = UIUtil.getLabelFont()
    val font = CodeReviewReactionsUIUtil.EMOJI_FONT?.deriveFont(labelFont.size2D) ?: labelFont
    PaintData(JBUI.scale(size), font)
  }

  private fun getPaintData(): PaintData =
    paintDataCache.getOrProvide(ScaleContext()) ?: PaintData(size, UIUtil.getLabelFont())

  private data class PaintData(val size: Int, val font: Font)
}