// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts

import com.intellij.application.options.colors.FontGlyphHashCache
import com.intellij.ide.HelpTooltip.Alignment
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import org.jetbrains.annotations.Nls
import java.awt.Font
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.Timer

/**
 * Popup that shows a two-line glyph comparison between [firstFont] and [secondFont].
 * Contains [com.intellij.ide.HelpTooltip]-like scheduling and positioning logic.
 */
internal class FontDiffPopup private constructor(
  private val firstFont: Font,
  private val secondFont: Font,
  private val previewText: @NlsSafe String,
) {
  private lateinit var tooltipPanel: JPanel

  private var popup: JBPopup? = null
  private var isOverPopup = false
  private var initialShowScheduled = false

  private var showTimer: Timer? = null
  private var hideTimer: Timer? = null
  private var dismissTimer: Timer? = null

  fun dispose() {
    hidePopup()
    cancelShowTimer(); cancelHideTimer(); cancelDismissTimer()
  }

  private fun grayLabel(@NlsContexts.Label text: String): JLabel {
    return JLabel(text).apply { foreground = UIUtil.getContextHelpForeground() }
  }

  fun installOn(owner: JComponent) {
    if (previewText.isBlank()) return

    val text1 = createTooltipTextArea(previewText, firstFont)
    val text2 = createTooltipTextArea(previewText, secondFont)

    tooltipPanel = panel {
      row(grayLabel(ApplicationBundle.message("settings.editor.font.diff.popup.default"))) {
        cell(text1)
      }
      row(grayLabel(ApplicationBundle.message("settings.editor.font.diff.popup.variant"))) {
        cell(text2)
      }
    }
      .withBorder(JBUI.Borders.empty(UIUtil.PANEL_REGULAR_INSETS))

    val mouseListener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        if (popup != null && popup!!.isVisible && !popup!!.isDisposed) {
          popup!!.cancel()
          popup = null
        }
        initialShowScheduled = true
        scheduleShow(Registry.intValue("ide.tooltip.initialReshowDelay", 500), owner, e.point)
      }

      override fun mouseExited(e: MouseEvent) {
        scheduleHide()
      }

      override fun mouseMoved(e: MouseEvent) {
        if (!initialShowScheduled) {
          scheduleShow(Registry.intValue("ide.tooltip.reshowDelay", 500), owner, e.point)
        }
      }
    }

    owner.addMouseListener(mouseListener)
    owner.addMouseMotionListener(mouseListener)

    owner.addHierarchyListener {
      if (!owner.isDisplayable) {
        dispose()
      }
    }
  }

  private fun createTooltipTextArea(@Nls text: String, f: Font): JTextArea = JTextArea(text).apply {
    isEditable = false
    isOpaque = false
    lineWrap = false
    font = f.deriveFont(UISettingsUtils.getInstance().scaleFontSize(f.size.toFloat()))
    border = JBUI.Borders.empty()
  }

  private fun cancelShowTimer() {
    showTimer?.stop(); showTimer = null
  }

  private fun cancelHideTimer() {
    hideTimer?.stop(); hideTimer = null
  }

  private fun cancelDismissTimer() {
    dismissTimer?.stop(); dismissTimer = null
  }

  private fun hidePopup() {
    initialShowScheduled = false
    cancelShowTimer()
    cancelDismissTimer()
    popup?.apply {
      if (!isDisposed && isVisible) cancel()
    }
    popup = null
  }

  private fun scheduleHide() {
    cancelHideTimer()
    hideTimer = Timer(Registry.intValue("ide.tooltip.initialDelay.highlighter", 500)) {
      hidePopup()
    }.apply {
      isRepeats = false
      start()
    }
  }

  private fun installPopupHoverTracking(p: JBPopup) {
    val listener = object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        isOverPopup = true
        cancelHideTimer()
      }

      override fun mouseExited(e: MouseEvent) {
        isOverPopup = false
        scheduleHide()
      }
    }
    p.content.addMouseListener(listener)
  }

  private fun showPopup(owner: JComponent, eventPoint: Point) {
    if (popup != null && popup!!.isVisible && !popup!!.isDisposed) return

    val builder = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(tooltipPanel, null)
      .setRequestFocus(false)
      .setFocusable(false)
      .setResizable(false)
      .setMovable(false)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(true)
      .setCancelOnWindowDeactivation(true)

    val p = builder.createPopup()
    popup = p

    val pointOnComponent = Alignment.CURSOR.getPointFor(owner, tooltipPanel.preferredSize, eventPoint)
    p.show(RelativePoint(owner, pointOnComponent))

    installPopupHoverTracking(p)
  }

  private fun scheduleShow(delayMs: Int, owner: JComponent, point: Point) {
    initialShowScheduled = true
    cancelShowTimer()
    showTimer = Timer(delayMs) {
      cancelShowTimer()
      showPopup(owner, point)
    }.apply {
      isRepeats = false
      start()
    }
  }

  companion object {
    private val programmingLigatures = listOf(
      "==", "===", "!=", "!==", "!~", "<>", "<=", ">=", "<=>",
      "->", "=>", "<-", "<--", "-->", "<->", "->>", "<<-", "|->", "~>", "<~", "<~>", "<~~", "~~>",
      "&&", "||", "!!", "??", "?.", "?:",
      "<<", ">>", ">>>", "<<<",
      ":=", "::=", "+=", "-=", "*=", "/=", "//=", "%=", "&=", "|=", "||=", "^=",
      "//", "///", "/*", "*/", "/**", "<!--", "{-", "-}", "\\\\",
      "::", ":::", "..", ".-", "...", "..=", "..-", "..<", ":-", "-:-", ":>:", ":<:", ".=",
      "++", "+++", "--", "**", "***", "%%", "##", "###",
      "<*>", "<$>", "<+>", "<<=", ">>=", "=<<", "~=", "=~",
      "[|", "|]", "/\\", "\\/", "|>", "<|", "<|>", "{|", "|}", "[<", ">]", "{.", ".}", "[]", "()", "{}",
      "</", "/>", "<?", "?>", "<%", "%>",
      "#[", "#(", "#![",
      "@_", "__", "0x",
    )

    fun diffForFeatures(fontGlyphCache: FontGlyphHashCache, scheme: EditorColorsScheme, previewChars: String, vararg features: String): FontDiffPopup {
      val base = JBR.getFontExtensions().deriveFontWithFeatures(scheme.getFont(EditorFontType.PLAIN))
      val derivedFont = JBR.getFontExtensions().deriveFontWithFeatures(scheme.getFont(EditorFontType.PLAIN), *features)

      val baseKey = fontGlyphCache.computeCaches(base, previewChars)
      val derivedKey = fontGlyphCache.computeCaches(derivedFont, previewChars)

      var previewText = previewChars
        .mapNotNull { char -> if (fontGlyphCache.getGlyphCache(baseKey, char) != fontGlyphCache.getGlyphCache(derivedKey, char)) char else null }
        .joinToString(" ")

      fontGlyphCache.computeLigatureCaches(base, programmingLigatures)
      fontGlyphCache.computeLigatureCaches(derivedFont, programmingLigatures)

      val ligatures = programmingLigatures
        .filter { ligature: String ->
          fontGlyphCache.run {
            val c1 = getLigatureCache(baseKey, ligature)
            val c2 = getLigatureCache(derivedKey, ligature)
            c1 != null && c2 != null && c1.zip(c2).all { it.first != it.second }
          }
        }
        .joinToString(" ")

      previewText = when {
        previewText.isEmpty() -> ligatures
        else -> "$previewText $ligatures"
      }

      return FontDiffPopup(base, derivedFont, previewText)
    }
  }
}
