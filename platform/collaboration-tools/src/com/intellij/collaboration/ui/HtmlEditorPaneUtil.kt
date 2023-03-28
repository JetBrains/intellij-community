// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ide.ui.AntialiasingType
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.*
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import java.awt.Shape
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.InlineView
import javax.swing.text.html.StyleSheet

/**
 * Read-only editor pane intended to display simple HTML snippet
 */
@Suppress("FunctionName")
fun SimpleHtmlPane(additionalStyleSheet: StyleSheet? = null, @Language("HTML") body: @Nls String? = null): JEditorPane =
  JEditorPane().apply {
    editorKit = HTMLEditorKitBuilder().withViewFactoryExtensions(
      ExtendableHTMLViewFactory.Extensions.WORD_WRAP,
      HtmlEditorPaneUtil.CONTENT_TOOLTIP
    ).apply {
      if (additionalStyleSheet != null) {
        val defaultStyleSheet = StyleSheetUtil.getDefaultStyleSheet()
        additionalStyleSheet.addStyleSheet(defaultStyleSheet)
        withStyleSheet(additionalStyleSheet)
      }
    }.build()

    isEditable = false
    isOpaque = false
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    margin = JBInsets.emptyInsets()
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent())

    (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE

    name = "Simple HTML Pane"

    if (body != null) {
      setHtmlBody(body)
    }
  }

/**
 * Read-only editor pane intended to display simple HTML snippet
 */
@Suppress("FunctionName")
fun SimpleHtmlPane(@Language("HTML") body: @Nls String? = null): JEditorPane = SimpleHtmlPane(null, body)

fun JEditorPane.setHtmlBody(@Language("HTML") @Nls body: String) {
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

object HtmlEditorPaneUtil {
  /**
   * Show tooltip from HTML title attribute
   *
   * Syntax is `<{CONTENT_TAG} title="{text}">`
   */
  val CONTENT_TOOLTIP: ExtendableHTMLViewFactory.Extension = ContentTooltipExtension()
}

private class ContentTooltipExtension : ExtendableHTMLViewFactory.Extension {
  override fun invoke(elem: Element, defaultView: View): View? {
    if (defaultView !is InlineView) return null

    return object : InlineView(elem) {
      override fun getToolTipText(x: Float, y: Float, allocation: Shape?): String? {
        val title = element.attributes.getAttribute(HTML.Attribute.TITLE) as? String
        if (!title.isNullOrEmpty()) {
          return title
        }

        return super.getToolTipText(x, y, allocation)
      }
    }
  }
}
