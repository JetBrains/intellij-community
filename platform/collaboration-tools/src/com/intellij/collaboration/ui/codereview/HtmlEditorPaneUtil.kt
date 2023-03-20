// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview

import com.intellij.ui.HyperlinkAdapter
import org.jetbrains.annotations.Nls
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

fun JEditorPane.setHtmlBody(@Nls body: String) {
  if (body.isEmpty()) {
    text = ""
  }
  else {
    text = "<html><body>$body</body></html>"
  }
  setSize(Int.MAX_VALUE / 2, Int.MAX_VALUE / 2)
}

fun JEditorPane.onHyperlinkActivated(listener: (HyperlinkEvent) -> Unit) {
  addHyperlinkListener(object : HyperlinkAdapter() {
    override fun hyperlinkActivated(e: HyperlinkEvent) {
      listener(e)
    }
  })
}
