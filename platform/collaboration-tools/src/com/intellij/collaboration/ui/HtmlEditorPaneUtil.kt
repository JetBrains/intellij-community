// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.html.AsyncHtmlImageLoader
import com.intellij.collaboration.ui.html.ResizingHtmlImageView
import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.*
import org.intellij.lang.annotations.Language
import java.awt.Graphics
import java.awt.Shape
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.html.*

/**
 * Read-only editor pane intended to display simple HTML snippet
 */
@Suppress("FunctionName")
fun SimpleHtmlPane(
  additionalStyleSheet: StyleSheet? = null,
  addBrowserListener: Boolean = true,
  customImageLoader: AsyncHtmlImageLoader? = null,
  baseUrl: URL? = null
): JEditorPane =
  JTextPane().apply {
    editorKit = HTMLEditorKitBuilder().withViewFactoryExtensions(
      ExtendableHTMLViewFactory.Extensions.WORD_WRAP,
      HtmlEditorPaneUtil.CONTENT_TOOLTIP,
      HtmlEditorPaneUtil.INLINE_ICON_EXTENSION,
      HtmlEditorPaneUtil.IMAGES_EXTENSION
    ).apply {
      if (additionalStyleSheet != null) {
        val defaultStyleSheet = StyleSheetUtil.getDefaultStyleSheet()
        additionalStyleSheet.addStyleSheet(defaultStyleSheet)
        withStyleSheet(additionalStyleSheet)
      }
    }.build()

    isEditable = false
    isOpaque = false
    if (addBrowserListener) {
      addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    }
    margin = JBInsets.emptyInsets()
    GraphicsUtil.setAntialiasingType(this, AntialiasingType.getAAHintForSwingComponent())

    (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE

    if (customImageLoader != null) {
      document.putProperty(AsyncHtmlImageLoader.KEY, customImageLoader)
    }
    if (baseUrl != null) {
      (document as HTMLDocument).base = baseUrl
    }

    name = "Simple HTML Pane"
  }

/**
 * Read-only editor pane intended to display simple HTML snippet
 */
@Suppress("FunctionName")
fun SimpleHtmlPane(@Language("HTML") body: String): JEditorPane = SimpleHtmlPane().apply {
  setHtmlBody(body)
}

fun JEditorPane.setHtmlBody(@Language("HTML") body: String) {
  if (body.isEmpty()) {
    text = ""
  }
  else {
    @Suppress("HardCodedStringLiteral")
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
  val CONTENT_TOOLTIP: ExtendableHTMLViewFactory.Extension = ContentTooltipExtension

  /**
   * Show an icon inlined with the text
   *
   * Syntax is `<icon-inline src="..."/>`
   */
  val INLINE_ICON_EXTENSION: ExtendableHTMLViewFactory.Extension = InlineIconExtension

  /**
   * Handles image loading and scaling
   */
  val IMAGES_EXTENSION: ExtendableHTMLViewFactory.Extension = ScalingImageExtension
}

private object ContentTooltipExtension : ExtendableHTMLViewFactory.Extension {
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

private object InlineIconExtension : ExtendableHTMLViewFactory.Extension {
  const val ICON_INLINE_ELEMENT_NAME = "icon-inline" // NON-NLS

  override fun invoke(elem: Element, view: View): View {
    if (ICON_INLINE_ELEMENT_NAME == elem.name) {
      val icon = elem.attributes.getAttribute(HTML.Attribute.SRC)?.let {
        val path = it as String

        IconLoader.findIcon(path, ExtendableHTMLViewFactory::class.java, true, false)
      }

      if (icon != null) {
        return object : InlineView(elem) {

          override fun getPreferredSpan(axis: Int): Float {
            return when (axis) {
              X_AXIS -> icon.iconWidth.toFloat() + super.getPreferredSpan(axis)
              else -> super.getPreferredSpan(axis)
            }
          }

          override fun paint(g: Graphics, allocation: Shape) {
            super.paint(g, allocation)
            icon.paintIcon(null, g, allocation.bounds.x, allocation.bounds.y)
          }
        }
      }
    }
    return view
  }
}

private object ScalingImageExtension : ExtendableHTMLViewFactory.Extension {
  override fun invoke(elem: Element, view: View): View {
    if (view is ImageView) {
      return ResizingHtmlImageView(elem)
    }
    return view
  }
}
