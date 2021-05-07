// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.profile.codeInspection.ui

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.HintHint
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.Color
import java.awt.Point
import java.io.IOException
import java.io.StringReader
import javax.swing.JEditorPane
import javax.swing.text.html.HTMLEditorKit

open class DescriptionEditorPane : JEditorPane(UIUtil.HTML_MIME, EMPTY_HTML) {

  init {
    isEditable = false
    isOpaque = false
    val css = (this.editorKit as HTMLEditorKit).styleSheet
    with(EditorColorsManager.getInstance().globalScheme) {
      css.addRule("a {overflow-wrap: anywhere;}")
      css.addRule("pre {padding:10px; background:#"
                  + ColorUtil.toHex(ObjectUtils.notNull(getColor(EditorColors.READONLY_BACKGROUND_COLOR), defaultBackground))
                  + ";}")
    }
  }

  override fun getBackground(): Color = UIUtil.getLabelBackground()

  companion object {
    const val EMPTY_HTML = "<html><body></body></html>"
  }

}

fun JEditorPane.readHTML(text: String) {
  try {
    read(StringReader(text), null)
  }
  catch (e: IOException) {
    throw RuntimeException(e)
  }
}

fun JEditorPane.toHTML(text: @Nls String?, miniFontSize: Boolean): String {
  val hintHint = HintHint(this, Point(0, 0))
  hintHint.setFont(if (miniFontSize) UIUtil.getLabelFont(UIUtil.FontSize.SMALL) else UIUtil.getLabelFont())
  return HintUtil.prepareHintText(text!!, hintHint)
}
