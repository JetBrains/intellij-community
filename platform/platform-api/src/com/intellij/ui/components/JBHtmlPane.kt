// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.ui.components.JBHtmlPaneStyleSheetRulesProvider.getStyleSheet
import com.intellij.util.containers.addAllIfNotNull
import com.intellij.util.ui.*
import com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions.icons
import com.intellij.util.ui.accessibility.ScreenReader
import com.intellij.util.ui.html.cssMargin
import com.intellij.util.ui.html.cssPadding
import com.intellij.util.ui.html.width
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Graphics
import java.awt.Image
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.net.URL
import java.util.*
import javax.swing.Icon
import javax.swing.JEditorPane
import javax.swing.KeyStroke
import javax.swing.text.Document
import javax.swing.text.EditorKit
import javax.swing.text.StyledDocument
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

@Suppress("LeakingThis")
open class JBHtmlPane(private val myStylesConfiguration: JBHtmlPaneStyleSheetRulesProvider.Configuration,
                      private val myPaneConfiguration: Configuration
) : JEditorPane(), Disposable {


  data class Configuration(
    val keyboardActions: Map<KeyStroke, ActionListener> = emptyMap(),
    val imageResolverFactory: (JBHtmlPane) -> Dictionary<URL, Image>? = { null },
    val iconResolver: (String) -> Icon? = { null },
    val additionalStyleSheetProvider: (backgroundColor: Color) -> List<StyleSheet> = { emptyList() },
    val fontResolver: CSSFontResolver? = null,
    val extensions: List<ExtendableHTMLViewFactory.Extension> = emptyList()
  )

  private var myText: @Nls String? = "" // getText() surprisingly crashes…, let's cache the text
  private var myCurrentDefaultStyleSheet: StyleSheet? = null
  private val mutableBackgroundFlow: MutableStateFlow<Color>

  init {
    enableEvents(AWTEvent.KEY_EVENT_MASK)
    isEditable = false
    if (ScreenReader.isActive()) {
      // Note: Making the caret visible is merely for convenience
      caret.isVisible = true
    }
    else {
      putClientProperty("caretWidth", 0) // do not reserve space for caret (making content one pixel narrower than the component)

      UIUtil.doNotScrollToCaret(this)
    }
    mutableBackgroundFlow = MutableStateFlow(background)
    val extensions = ArrayList(myPaneConfiguration.extensions)
    extensions.addAllIfNotNull(
      icons { key: String -> myPaneConfiguration.iconResolver(key) },
      ExtendableHTMLViewFactory.Extensions.BASE64_IMAGES,
      ExtendableHTMLViewFactory.Extensions.INLINE_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.PARAGRAPH_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.LINE_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.BLOCK_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.WBR_SUPPORT,
      ExtendableHTMLViewFactory.Extensions.HIDPI_IMAGES.takeIf {
        !myPaneConfiguration.extensions.contains(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES)
      }
    )

    val editorKit = HTMLEditorKitBuilder()
      .replaceViewFactoryExtensions(*extensions.toTypedArray())
      .withViewFactoryExtensions()
      .withFontResolver(myPaneConfiguration.fontResolver ?: EditorCssFontResolver.getGlobalInstance())
      .build()
    updateDocumentationPaneDefaultCssRules(editorKit)

    addPropertyChangeListener { evt: PropertyChangeEvent ->
      val propertyName = evt.propertyName
      if ("background" == propertyName || "UI" == propertyName) {
        updateDocumentationPaneDefaultCssRules(editorKit)
        mutableBackgroundFlow.value = background
      }
    }

    super.setEditorKit(editorKit)
    border = JBUI.Borders.empty()
  }

  override fun dispose() {
    caret.isVisible = false // Caret, if blinking, has to be deactivated.
  }

  override fun getText(): @Nls String? {
    return myText
  }

  override fun setText(t: @Nls String?) {
    myText = t
    super.setText(t)
  }

  override fun setEditorKit(kit: EditorKit) {
    throw UnsupportedOperationException("Cannot change EditorKit for JBHtmlPane")
  }

  val backgroundFlow: StateFlow<Color>
    get() = mutableBackgroundFlow

  private fun updateDocumentationPaneDefaultCssRules(editorKit: HTMLEditorKit) {
    val editorStyleSheet = editorKit.styleSheet
    myCurrentDefaultStyleSheet
      ?.let { editorStyleSheet.removeStyleSheet(it) }
    val newStyleSheet = StyleSheet()
      .also { myCurrentDefaultStyleSheet = it }
    val background = background
    newStyleSheet.addStyleSheet(getStyleSheet(background, myStylesConfiguration))
    for (styleSheet in myPaneConfiguration.additionalStyleSheetProvider(background)) {
      newStyleSheet.addStyleSheet(styleSheet)
    }
    editorStyleSheet.addStyleSheet(newStyleSheet)
  }

  override fun processKeyEvent(e: KeyEvent) {
    val keyStroke = KeyStroke.getKeyStrokeForEvent(e)
    val listener = myPaneConfiguration.keyboardActions[keyStroke]
    if (listener != null) {
      listener.actionPerformed(ActionEvent(this, 0, ""))
      e.consume()
      return
    }
    super.processKeyEvent(e)
  }

  override fun paintComponent(g: Graphics) {
    GraphicsUtil.setupAntialiasing(g)
    super.paintComponent(g)
  }

  override fun setDocument(doc: Document) {
    super.setDocument(doc)
    doc.putProperty("IgnoreCharsetDirective", true)
    if (doc is StyledDocument) {
      doc.putProperty("imageCache", myPaneConfiguration.imageResolverFactory(this))
    }
  }

  protected fun getPreferredSectionWidth(sectionClassName: String): Int {
    val definition = findSection(getUI().getRootView(this), sectionClassName)
    var result = definition?.getPreferredSpan(View.X_AXIS)?.toInt() ?: -1
    if (result > 0) {
      result += definition!!.cssMargin.width
      var parent = definition.parent
      while (parent != null) {
        result += parent.cssMargin.width + parent.cssPadding.width
        parent = parent.parent
      }
    }
    return result
  }

  private fun findSection(view: View, sectionClassName: String): View? {
    if (sectionClassName == view.element.attributes.getAttribute(HTML.Attribute.CLASS)) {
      return view
    }
    for (i in 0 until view.viewCount) {
      val definition = findSection(view.getView(i), sectionClassName)
      if (definition != null) {
        return definition
      }
    }
    return null
  }
}
