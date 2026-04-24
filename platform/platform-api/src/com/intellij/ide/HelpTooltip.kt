// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.util.text.HtmlChunk
import javax.swing.JComponent

/**
 * Sets tooltip content.
 * Prefer this method over [JComponent.setToolTipText] to avoid accidental HTML injections.
 *
 * Tooltip text is allowed to contain HTML markup. Construct the text using [HtmlChunk].
 * If your tooltip doesn't suppose to contain HTML markup,
 * prefer using [HtmlChunk.text] to avoid accidental HTML injections.
 */
fun JComponent.setToolTipText(html: HtmlChunk?) {
  @Suppress("UseHtmlChunkToolTip")
  this.toolTipText = html?.toString()
}