// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk.*
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JEditorPane
import javax.swing.text.html.HTMLEditorKit

private const val PADDING = "padding-left:15px;padding-right:15px;padding-top:10px;padding-bottom:10px;"

/**
 * @author Alexander Lobas
 */
@ApiStatus.Internal
class SuggestedComponent : JEditorPane() {
  init {
    UIUtil.convertToLabel(this)
    caret = EmptyCaret.INSTANCE

    val color = JBColor.namedColor("Plugins.suggestedLabelBackground", 0xF2FCF3, 0x253627)
    val sheet = (editorKit as HTMLEditorKit).styleSheet
    sheet.addRule("div {background-color: " + ColorUtil.toHtmlColor(color) + "}")
  }

  fun setSuggestedText(@NlsSafe text: String?) {
    isVisible = text != null

    if (text != null) {
      setText(html().child(div(PADDING).child(tag("center").addRaw(text))).toString())
    }
  }
}