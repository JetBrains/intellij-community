// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.ActionLink
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleType
import com.intellij.ui.svg.getSvgDocumentSize
import com.intellij.ui.svg.renderSvgWithSize
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Font
import java.io.ByteArrayInputStream
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JTextArea
import kotlin.math.min

/** Swing shows the text of a label or a button as plain text when this client property is true. */
internal const val HTML_DISABLE_PROPERTY = "html.disable"

/** The SVG data of the light and the dark variant of one image. */
@ApiStatus.Internal
class MigrationImageData(val light: ByteArray, val dark: ByteArray)

/** Renders the [isDark] variant of [image] so that it fits into [maxWidth] and [maxHeight]. */
@ApiStatus.Internal
fun migrationSvgIcon(image: MigrationImageData, maxWidth: Int, maxHeight: Int, isDark: Boolean): Icon {
  val data = if (isDark) image.dark else image.light
  val size = getSvgDocumentSize(data)
  val ratio = min(maxWidth / size.width.coerceAtLeast(1f), maxHeight / size.height.coerceAtLeast(1f))
  val width = size.width.coerceAtLeast(1f) * ratio
  val height = size.height.coerceAtLeast(1f) * ratio
  val context = ScaleContext.create()
  val rendered = renderSvgWithSize(ByteArrayInputStream(data), width, height, context.getScale(ScaleType.SYS_SCALE).toFloat())
  return JBImageIcon(ImageUtil.ensureHiDPI(rendered, context, width.toDouble(), height.toDouble()))
}

/**
 * A wrapped text that Swing never renders as HTML. The migration dialog shows server texts with it.
 */
@ApiStatus.Internal
fun plainTextArea(text: @NlsSafe String, font: Font, width: Int): JTextArea {
  return PlainTextArea(text).apply {
    isEditable = false
    isFocusable = false
    isOpaque = false
    lineWrap = true
    wrapStyleWord = true
    border = JBUI.Borders.empty()
    this.font = font
    foreground = UIUtil.getLabelForeground()
    // a wrapped text area computes its height from its current width
    setSize(width, Short.MAX_VALUE.toInt())
    preferredSize = Dimension(width, preferredSize.height)
  }
}

/** A read-only text. A screen reader announces it as a label, not as a text field. */
private class PlainTextArea(text: @NlsSafe String) : JTextArea(text) {
  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleJTextArea() {
        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.LABEL
      }
    }
    return accessibleContext
  }
}

/**
 * A button whose text Swing never renders as HTML.
 */
@ApiStatus.Internal
fun plainTextButton(text: @NlsSafe String, action: () -> Unit): JButton {
  return JButton().apply {
    putClientProperty(HTML_DISABLE_PROPERTY, true)
    this.text = text
    addActionListener { action() }
  }
}

/**
 * A link whose text Swing never renders as HTML.
 */
@ApiStatus.Internal
fun plainTextLink(text: @NlsSafe String, action: () -> Unit): ActionLink {
  return ActionLink().apply {
    putClientProperty(HTML_DISABLE_PROPERTY, true)
    this.text = text
    setExternalLinkIcon()
    addActionListener { action() }
  }
}
