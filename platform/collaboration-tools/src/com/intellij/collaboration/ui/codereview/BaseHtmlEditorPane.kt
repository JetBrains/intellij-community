// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.Nls
import javax.swing.JEditorPane
import javax.swing.text.DefaultCaret

open class BaseHtmlEditorPane : JEditorPane() {

  init {
    editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()

    isEditable = false
    isOpaque = false
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    margin = JBInsets.emptyInsets()
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent())

    val caret = caret as DefaultCaret
    caret.updatePolicy = DefaultCaret.NEVER_UPDATE
  }

  fun setBody(@Nls body: String) {
    if (body.isEmpty()) {
      text = ""
    }
    else {
      text = "<html><body>$body</body></html>"
    }
    setSize(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
  }
}