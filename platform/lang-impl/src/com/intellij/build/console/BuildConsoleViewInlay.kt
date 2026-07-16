// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.BuildBundle
import com.intellij.build.console.CollapsingPanel.CollapseState.COLLAPSED
import com.intellij.build.console.CollapsingPanel.CollapseState.EXPANDED
import com.intellij.icons.AllIcons.General
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors.DOC_COMMENT
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.observable.util.equalsTo
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.event.HyperlinkListener
import com.intellij.ui.dsl.builder.panel as panelDsl

internal object BuildConsoleViewInlay {

  enum class Kind { INFO, WARNING, ERROR }

  private val CONSOLE_INLAY_COMMENT = TextAttributesKey.createTextAttributesKey("CONSOLE_INLAY_COMMENT", DOC_COMMENT)
  private val CONSOLE_INLAY_PANEL = TextAttributesKey.createTextAttributesKey("CONSOLE_INLAY_PANEL")
  private val CONSOLE_INLAY_PANEL_BACKGROUND = JBUI.CurrentTheme.ToolWindow.stripeBackground()

  fun comment(
    colorsScheme: EditorColorsScheme,
    text: String,
    hyperlinkListener: HyperlinkListener?,
  ): JComponent {
    val textAttributes = colorsScheme.getAttributes(CONSOLE_INLAY_COMMENT)
    val textFont = colorsScheme.getConsoleFont(textAttributes, text)
    val textForeground = textAttributes.foregroundColor ?: colorsScheme.defaultForeground
    val textBackground = textAttributes.backgroundColor ?: colorsScheme.defaultBackground
    return panelDsl {
      row {
        cell(JBHtmlPane()).applyToComponent {
          hyperlinkListener?.let { addHyperlinkListener(it) }
          this.text = text
          this.isOpaque = textAttributes.backgroundColor != null
          this.font = textFont
          this.foreground = textForeground
          this.background = textBackground
          this.border = JBUI.Borders.empty(8, 12)
        }
      }
    }
  }

  fun panel(
    colorsScheme: EditorColorsScheme,
    text: String,
    kind: Kind,
    hyperlinkListener: HyperlinkListener?,
  ): JComponent {
    val textAttributes = colorsScheme.getAttributes(CONSOLE_INLAY_PANEL)
    val textFont = colorsScheme.getConsoleFont(textAttributes, text)
    val textForeground = textAttributes.foregroundColor ?: colorsScheme.defaultForeground
    val textBackground = textAttributes.backgroundColor ?: CONSOLE_INLAY_PANEL_BACKGROUND

    val textComponent = CollapsingPanel(5, 20, JBHtmlPane().apply {
      hyperlinkListener?.let { addHyperlinkListener(it) }
      this.text = text
      this.isOpaque = false
      this.font = textFont
      this.foreground = textForeground
      this.border = JBUI.Borders.empty()
    })

    return panelDsl {
      row {
        customize(UnscaledGapsY(bottom = 12, top = 12))

        icon(kind.getInlayIcon())
          .align(AlignX.LEFT)
          .align(AlignY.TOP)
          .customize(UnscaledGaps(1, 12, 0, 8))
          .applyToComponent {
            this.isOpaque = false
            this.border = JBUI.Borders.empty()
          }

        cell(textComponent)
          .align(AlignX.FILL)
          .align(AlignY.TOP)
          .customize(UnscaledGaps(0))
      }
      row {
        link(BuildBundle.message("build.console.inlay.show.more")) { textComponent.collapseStateProperty.set(EXPANDED) }
          .visibleIf(textComponent.collapseStateProperty.equalsTo(COLLAPSED))
          .customize(UnscaledGaps(0, 36, 12, 0))
        link(BuildBundle.message("build.console.inlay.show.less")) { textComponent.collapseStateProperty.set(COLLAPSED) }
          .visibleIf(textComponent.collapseStateProperty.equalsTo(EXPANDED))
          .customize(UnscaledGaps(0, 36, 12, 0))
      }
    }.apply {
      this.isOpaque = false
      this.border = JBUI.Borders.empty()
    }.rounded {
      this.isOpaque = true
      this.background = textBackground
      this.border = JBUI.Borders.empty()
    }.padded()
  }

  private fun Component.padded(): JComponent {
    return JBUI.Panels.simplePanel(this)
      .withBorder(JBUI.Borders.empty(12, 0))
      .andTransparent()
  }

  private fun Component.rounded(configure: JComponent.() -> Unit): JComponent {
    return RoundedPanel(12, 12)
      .addToCenter(this)
      .apply(configure)
  }

  private fun Kind.getInlayIcon(): Icon = when (this) {
    Kind.INFO -> General.Information
    Kind.WARNING -> General.Warning
    Kind.ERROR -> General.Error
  }

  private fun EditorColorsScheme.getConsoleFont(attributes: TextAttributes, text: String): Font {
    val fontType = EditorFontType.forJavaStyle(attributes.fontType)
    val consoleFontType = EditorFontType.getConsoleType(fontType)
    val original = getFont(consoleFontType)
    return UIUtil.getFontWithFallbackIfNeeded(original, text)
  }

  private class RoundedPanel(
    private val arcWidth: Int,
    private val arcHeight: Int,
  ) : BorderLayoutPanel() {

    override fun paintComponent(g: Graphics) {
      g.useCopy { graphics ->
        GraphicsUtil.setupRoundedBorderAntialiasing(graphics)
        graphics.color = background
        graphics.fillRoundRect(0, 0, width, height, arcWidth, arcHeight)
      }
    }

    private inline fun <reified G : Graphics, T> G.useCopy(block: (G) -> T): T {
      val local = create() as G
      try {
        return block(local)
      }
      finally {
        local.dispose()
      }
    }
  }
}
