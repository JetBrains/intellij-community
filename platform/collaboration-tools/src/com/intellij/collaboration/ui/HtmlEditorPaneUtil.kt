// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.ui.Graphics2DDelegate
import com.intellij.ui.HyperlinkAdapter
import com.intellij.util.ui.*
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.image.ImageObserver
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret
import javax.swing.text.Element
import javax.swing.text.FlowView
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.ImageView
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
  val CONTENT_TOOLTIP: ExtendableHTMLViewFactory.Extension = ContentTooltipExtension

  /**
   * Show an icon inlined with the text
   *
   * Syntax is `<icon-inline src="..."/>`
   */
  val INLINE_ICON_EXTENSION: ExtendableHTMLViewFactory.Extension = InlineIconExtension

  /**
   * Handles image scaling
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
      return MyScalingImageView(elem)
    }
    return view
  }

  // Copied from: com.intellij.codeInsight.documentation.render.DocRenderer.MyScalingImageView
  private class MyScalingImageView(element: Element) : ImageView(element) {

    private var myAvailableWidth = 0

    override fun getResizeWeight(axis: Int) = 1

    override fun getMaximumSpan(axis: Int) = getPreferredSpan(axis)

    override fun getPreferredSpan(axis: Int): Float {
      val baseSpan = super.getPreferredSpan(axis)
      if (axis == X_AXIS) return baseSpan

      var availableWidth = getAvailableWidth()
      if (availableWidth <= 0) return baseSpan

      val baseXSpan = super.getPreferredSpan(X_AXIS)
      if (baseXSpan <= 0) return baseSpan

      if (availableWidth > baseXSpan) {
        availableWidth = baseXSpan.toInt()
      }
      if (myAvailableWidth > 0 && availableWidth != myAvailableWidth) {
        preferenceChanged(null, false, true)
      }
      myAvailableWidth = availableWidth

      return baseSpan * availableWidth / baseXSpan
    }

    private fun getAvailableWidth(): Int {
      var v: View? = this
      while (v != null) {
        val parent = v.parent
        if (parent is FlowView) {
          val childCount = parent.getViewCount()
          for (i in 0 until childCount) {
            if (parent.getView(i) === v) {
              return parent.getFlowSpan(i)
            }
          }
        }
        v = parent
      }
      return 0
    }

    override fun paint(g: Graphics, a: Shape) {
      val targetRect = if (a is Rectangle) a else a.bounds
      val scalingGraphics = object : Graphics2DDelegate(g as Graphics2D) {
        override fun drawImage(img: Image, x: Int, y: Int, width: Int, height: Int, observer: ImageObserver): Boolean {
          var newWidth = width
          var newHeight = height

          val maxWidth = 0.coerceAtLeast(targetRect.width - 2 * (x - targetRect.x)) // assuming left and right insets are the same
          val maxHeight = 0.coerceAtLeast(targetRect.height - 2 * (y - targetRect.y)) // assuming top and bottom insets are the same

          if (width > maxWidth) {
            newHeight = height * maxWidth / width
            newWidth = maxWidth
          }

          if (height > maxHeight) {
            newWidth = width * maxHeight / height
            newHeight = maxHeight
          }

          return super.drawImage(img, x, y, newWidth, newHeight, observer)
        }
      }
      super.paint(scalingGraphics, a)
    }
  }
}
