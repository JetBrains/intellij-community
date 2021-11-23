// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview

import com.intellij.ide.ui.AntialiasingType
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBHtmlEditorKit
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Shape
import javax.swing.JEditorPane
import javax.swing.SizeRequirements
import javax.swing.text.DefaultCaret
import javax.swing.text.Element
import javax.swing.text.View
import javax.swing.text.ViewFactory
import javax.swing.text.html.HTML
import javax.swing.text.html.InlineView
import javax.swing.text.html.ParagraphView
import kotlin.math.max

private const val ICON_INLINE_ELEMENT_NAME = "icon-inline" // NON-NLS

open class BaseHtmlEditorPane(viewFactory: ViewFactory) : JEditorPane() {

  constructor(iconsClass: Class<*>): this(HtmlEditorViewFactory(iconsClass))

  init {
    editorKit = JBHtmlEditorKit(viewFactory, true)

    isEditable = false
    isOpaque = false
    addHyperlinkListener(BrowserHyperlinkListener.INSTANCE)
    margin = JBUI.emptyInsets()
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

  protected open class HtmlEditorViewFactory(private val iconsClass: Class<*>) : JBHtmlEditorKit.JBHtmlFactory() {
    override fun create(elem: Element): View {
      if (ICON_INLINE_ELEMENT_NAME == elem.name) {
        val icon = elem.attributes.getAttribute(HTML.Attribute.SRC)?.let {
          val path = it as String

          IconLoader.findIcon(path, iconsClass)
        }

        if (icon != null) {
          return object : InlineView(elem) {

            override fun getPreferredSpan(axis: Int): Float {
              when (axis) {
                View.X_AXIS -> return icon.iconWidth.toFloat() + super.getPreferredSpan(axis)
                else -> return super.getPreferredSpan(axis)
              }
            }

            override fun paint(g: Graphics, allocation: Shape) {
              super.paint(g, allocation)
              icon.paintIcon(null, g, allocation.bounds.x, allocation.bounds.y)
            }
          }
        }
      }

      val view = super.create(elem)
      if (view is ParagraphView) {
        return MyParagraphView(elem)
      }
      return view
    }
  }

  protected open class MyParagraphView(elem: Element) : ParagraphView(elem) {
    override fun calculateMinorAxisRequirements(axis: Int, r: SizeRequirements?): SizeRequirements {
      var r = r
      if (r == null) {
        r = SizeRequirements()
      }
      r.minimum = layoutPool.getMinimumSpan(axis).toInt()
      r.preferred = max(r.minimum, layoutPool.getPreferredSpan(axis).toInt())
      r.maximum = Integer.MAX_VALUE
      r.alignment = 0.5f
      return r
    }
  }
}