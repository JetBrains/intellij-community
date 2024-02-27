// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.reactions

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.ComplementaryFontsRegistry
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.scale.JBUIScale
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
import java.awt.font.TextLayout
import java.awt.geom.Point2D
import javax.swing.Icon

object CodeReviewReactionsUIUtil {
  internal const val VARIATION_SELECTOR: String = "\uFE0F"

  const val BUTTON_HEIGHT: Int = 24

  const val HORIZONTAL_GAP: Int = 8

  const val ICON_SIZE: Int = 20
  const val EMOJI_FONT_SIZE: Float = 16f
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
 * and sets font size to [CodeReviewReactionsUIUtil.EMOJI_FONT_SIZE]
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
      val frc = g2d.fontMetrics.fontRenderContext
      val layout = TextLayout(text, g2d.font, frc)
      val textBounds = layout.bounds
      val offsetX = (paintData.size - textBounds.width).coerceAtLeast(0.0) / 2
      val offsetY = (paintData.size - textBounds.height).coerceAtLeast(0.0) / 2
      val baseline = Point2D.Double(offsetX - textBounds.x, offsetY - textBounds.y)

      g2d.color = UIUtil.getLabelForeground()
      layout.draw(g2d, baseline.x.toFloat(), baseline.y.toFloat())
    }
    finally {
      g2d.dispose()
    }
  }

  override fun getIconWidth(): Int = getPaintData().size
  override fun getIconHeight(): Int = getPaintData().size

  private val paintDataCache = ScaleContextCache {
    val labelFont = UIUtil.getLabelFont()
    val fontSize = CodeReviewReactionsUIUtil.EMOJI_FONT_SIZE
    val pref = FontPreferencesImpl().apply {
      setFontSize(labelFont.family, fontSize)
    }
    val font = ComplementaryFontsRegistry.getFontAbleToDisplay(text, 0, text.length, Font.PLAIN, pref, null).font
      .deriveFont(JBUIScale.scale(fontSize)) // we don't use font scale here, bc it's not a text, but icon
    PaintData(JBUI.scale(size), font)
  }

  private fun getPaintData(): PaintData =
    paintDataCache.getOrProvide(ScaleContext()) ?: PaintData(size, UIUtil.getLabelFont())

  private data class PaintData(val size: Int, val font: Font)
}