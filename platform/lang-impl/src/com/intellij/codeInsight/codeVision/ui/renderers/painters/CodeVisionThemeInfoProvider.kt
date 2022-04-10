// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.renderers.painters

import com.intellij.codeInsight.daemon.impl.HintUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Font

open class CodeVisionThemeInfoProvider {
  open fun foregroundColor(editor: Editor, hovered: Boolean): Color {
    return if (hovered) {
      JBUI.CurrentTheme.Link.Foreground.ENABLED
    }
    else {
      JBColor.GRAY
    }
  }

  open fun font(editor: Editor, style: Int = Font.PLAIN): Font {
    val size = lensFontSize(editor)
    return if (EditorSettingsExternalizable.getInstance().isUseEditorFontInInlays) {
      val editorFont = EditorUtil.getEditorFont()
      editorFont.deriveFont(style, size)
    }
    else {
      UIUtil.getLabelFont().deriveFont(style, size)
    }
  }

  open fun lensFontSize(editor: Editor) = HintUtil.getSize(editor)
}