// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.asSafely
import com.intellij.util.containers.addAllIfNotNull
import com.intellij.util.ui.*
import com.intellij.util.ui.ExtendableHTMLViewFactory.Extensions.icons
import com.intellij.util.ui.accessibility.ScreenReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.Nls
import java.awt.AWTEvent
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Image
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import java.net.URL
import java.util.*
import javax.swing.JEditorPane
import javax.swing.KeyStroke
import javax.swing.text.Document
import javax.swing.text.EditorKit
import javax.swing.text.StyledDocument
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet
import kotlin.math.roundToInt

/**
 * The [JBHtmlPane] allows to render HTML according to the IntelliJ UI guidelines.
 *
 * ### HTML support
 *
 * Additionally to tags supported by AWT HTML toolkit, [JBHtmlPane] supports:
 * - `<code>` - display code fragment, e.g:
 *     ```html
 *     <code>This is my code</code>
 *     ```
 * - `<pre><code>` or `<blockquote><pre>` - display code block, e.g:
 *     ```html
 *     <pre><code>
 *     System.out.println("Hello world")
 *     </code></pre>
 *     <blockquote>
 *       <pre>System.out.println("Hello other world")</pre>
 *     </blockquote>
 *     ```
 * - `<kbd>` - display a keyboard shortcut, e.g.
 *     ```html
 *     <kbd>⌘</kbd> <kbd>C</kbd>
 *     ```
 * - `<shortcut>` - special tag, which supports two attributes:
 *     - an IDE `actionId`
 *     - or `raw` keystroke, as supported by [KeyStroke.getKeyStroke]
 *     The tag is expanded to a sequence of `<kbd>` tags. E.g:
 *     ```html
 *     Start debug with <shortcut actionId="Debug"></shortcut>
 *     ```
 *     ```html
 *     Start debug with <shortcut raw="meta F8"/>
 *     ```
 *     Both can be expanded to
 *     ```html
 *     Start debug with <kbd>⌘</kbd> <kbd>F8</kbd>
 *     ```
 * - `<wbr>` - provides optional word breaking location
 * - `<samp>` - show a piece of text in editor font. Similar to `<code>`,
 *     but without any special formatting except for the font.
 * - `<hr>` - a horizontal line, no shade, with support for colors
 * - `<details>`/`<summary>` - collapsible section:
 *     ```html
 *     <details>
 *       <summary>Contents always visible, clickable chevron to the right to expand section</summary>
 *       <p>The rest of the contents visible after expanding the section</p>
 *     </details>
 *     ```
 *
 * ### CSS Support
 *
 * Additionally, to CSS properties supported by AWT HTML toolkit, [JBHtmlPane] supports:
 * - `border-radius`, e.g.:
 *     ```CSS
 *     code {
 *        border-radius: 8px
 *     }
 *     ```
 * - `line-height` (`px`, `%`, number), e.g.:
 *     ```CSS
 *     p {
 *        line-height: 120%;
 *        line-height: 1.2;
 *        line-height: 24px;
 *     }
 *     ```
 * - paddings and margins for inline elements (no nesting supported), e.g.:
 *     ```CSS
 *     span.padded {
 *        padding: 5px;
 *        margin: 0px 2px 1px 2px;
 *     }
 *     ```
 * - elements with class `editor-color-*` are styled according to the attributes from
 *   current editor color scheme, e.g.:
 *     ```HTML
 *     <span class='editor-color-DEFAULT_CONSTANT'>constant</span>
 *     ```
 *
 */
@Experimental
@Suppress("LeakingThis")
open class JBHtmlPane : JEditorPane, Disposable, ExtendableHTMLViewFactory.ScaledHtmlJEditorPane {

  private val service: ImplService = ApplicationManager.getApplication().service()
  private var myText: @Nls String = "" // getText() surprisingly crashes…, let's cache the text
  private var myCurrentDefaultStyleSheet: StyleSheet? = null
  private val mutableBackgroundFlow: MutableStateFlow<Color> = MutableStateFlow(background)
  private val myStyleConfiguration: JBHtmlPaneStyleConfiguration
  private val myPaneConfiguration: JBHtmlPaneConfiguration

  /**
   * Use this constructor to provide configuration for [com.intellij.ui.components.JBHtmlPane] upfront,
   * without the requirement to extend this class.
   * When this constructor is used, [initializePaneConfiguration] and [initializeStyleConfiguration]
   * methods are not called.
   */
  constructor(
    styleConfiguration: JBHtmlPaneStyleConfiguration,
    paneConfiguration: JBHtmlPaneConfiguration,
  ) : super() {
    this.myStyleConfiguration = styleConfiguration
    this.myPaneConfiguration = paneConfiguration
    init()
  }

  /**
   * Default constructor, the pane configuration is initialized using [initializePaneConfiguration]
   * and [initializeStyleConfiguration] methods.
   */
  constructor() : super() {
    myStyleConfiguration = JBHtmlPaneStyleConfiguration.builder().also { initializeStyleConfiguration(it) }.build()
    myPaneConfiguration = JBHtmlPaneConfiguration.builder().also { initializePaneConfiguration(it) }.build()
    init()
  }

  /**
   * Use the provided builder to initialize pane configuration. This method is called only if [JBHtmlPane]
   * is constructed using parameter-less constructor.
   */
  protected open fun initializePaneConfiguration(builder: JBHtmlPaneConfiguration.Builder) {
  }

  /**
   * Use the provided builder to initialize pane style configuration. This method is called only if [JBHtmlPane]
   *    * is constructed using parameter-less constructor.
   */
  protected open fun initializeStyleConfiguration(builder: JBHtmlPaneStyleConfiguration.Builder) {
  }

  private fun init() {
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
    val extensions = ArrayList(myPaneConfiguration.extensions)
    extensions.addAllIfNotNull(
      icons { key: String -> myPaneConfiguration.iconResolver(key) },
      ExtendableHTMLViewFactory.Extensions.INLINE_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.PARAGRAPH_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.LINE_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.BLOCK_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.FORM_VIEW_EX,
      ExtendableHTMLViewFactory.Extensions.WBR_SUPPORT,
      ExtendableHTMLViewFactory.Extensions.HIDPI_IMAGES.takeIf {
        !myPaneConfiguration.extensions.contains(ExtendableHTMLViewFactory.Extensions.FIT_TO_WIDTH_IMAGES)
      },
      ExtendableHTMLViewFactory.Extensions.BLOCK_HR_SUPPORT,
      ExtendableHTMLViewFactory.Extensions.DETAILS_SUMMARY_SUPPORT,
    )

    val editorKit = HTMLEditorKitBuilder()
      .replaceViewFactoryExtensions(*extensions.toTypedArray())
      .withFontResolver(myPaneConfiguration.fontResolver ?: service.defaultEditorCssFontResolver())
      .withUnderlinedHoveredHyperlink(myPaneConfiguration.underlinedHoveredHyperlink)
      .build()
    updateDocumentationPaneDefaultCssRules(editorKit)

    // The value might have changed already since the flow was created,
    // so we need to update it manually just before we register the listener.
    // For example, the background is changed when we set isEditable above.
    mutableBackgroundFlow.value = background
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

  override fun getSelectedText(): String? =
    // We need to replace zero-width space char used to represent <wbr>
    // in JBHtmlEditorKit.JBHtmlDocument.JBHtmlReader.addSpecialElement().
    // Swing HTML control does not accept elements with no text.
    super.getSelectedText()?.replace("\u200B", "")

  override fun getText(): @Nls String {
    return myText
  }

  override fun setText(t: @Nls String?) {
    if (t != null && t.length > 50000)
      thisLogger().warn("HTML pane text is very long (${t.length}): ${StringUtil.shortenTextWithEllipsis(t, 1000, 250, "<TRUNCATED>")}")
    myText = t?.let { service.transpileHtmlPaneInput(it) } ?: ""
    try {
      super.setText(myText)
    }
    catch (e: Throwable) {
      thisLogger().error("Failed to set contents of the HTML pane", e)
    }
  }

  override fun setFont(font: Font?) {
    super.setFont(font)
    editorKit.asSafely<HTMLEditorKit>()?.let {
      updateDocumentationPaneDefaultCssRules(it)
    }
  }

  override fun setEditorKit(kit: EditorKit) {
    throw UnsupportedOperationException("Cannot change EditorKit for JBHtmlPane")
  }

  val backgroundFlow: StateFlow<Color>
    get() = mutableBackgroundFlow

  /**
   * Override to provide an alternate scale factor for pane contents.
   *
   * Note: the control font must already be scaled by this factor because
   * some Swing internals render contents directly using the default
   * font size, ignoring CSS settings. Such an example is the list
   * view rendering logic.
   */
  override val contentsScaleFactor: Float
    get() = JBUIScale.scale(1.0f)

  /**
   * Forces all stylesheets to be regenerated if anything changed,
   * and reapplies CSS to all controls again.
   */
  fun reloadCssStylesheets() {
    updateDocumentationPaneDefaultCssRules(editorKit as HTMLEditorKit)
  }

  private fun updateDocumentationPaneDefaultCssRules(editorKit: HTMLEditorKit) {
    val editorStyleSheet = editorKit.styleSheet
    myCurrentDefaultStyleSheet
      ?.let { editorStyleSheet.removeStyleSheet(it) }
    val newStyleSheet = StyleSheet()
      .also { myCurrentDefaultStyleSheet = it }

    val contentsScaleFactor = contentsScaleFactor
    val baseFontSize = (font.size / contentsScaleFactor).roundToInt()

    newStyleSheet.addStyleSheet(service.getDefaultStyleSheet(background, contentsScaleFactor, baseFontSize, myStyleConfiguration))
    newStyleSheet.addStyleSheet(service.getEditorColorsSchemeStyleSheet(myStyleConfiguration.colorSchemeProvider()))
    myPaneConfiguration.customStyleSheetProviders.forEach {
      newStyleSheet.addStyleSheet(it(this))
    }
    editorStyleSheet.addStyleSheet(newStyleSheet)
    service.applyCssToView(this)
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
    service.ensureEditableViewsAreNotFocusable(this)
  }

  override fun setDocument(doc: Document) {
    super.setDocument(doc)
    doc.putProperty("IgnoreCharsetDirective", true)
    if (doc is StyledDocument) {
      doc.putProperty("imageCache", myPaneConfiguration.imageResolverFactory(this)
                                    ?: service.createDefaultImageResolver(this))
    }
  }

  @ApiStatus.Internal
  interface ImplService {

    fun transpileHtmlPaneInput(text: @Nls String): @Nls String

    fun defaultEditorCssFontResolver(): CSSFontResolver

    fun getDefaultStyleSheet(paneBackgroundColor: Color, scaleFactor: Float, baseFontSize: Int, configuration: JBHtmlPaneStyleConfiguration): StyleSheet

    fun getEditorColorsSchemeStyleSheet(editorColorsScheme: EditorColorsScheme): StyleSheet

    fun createDefaultImageResolver(pane: JBHtmlPane): Dictionary<URL, Image>

    fun applyCssToView(pane: JBHtmlPane)

    fun ensureEditableViewsAreNotFocusable(pane: JBHtmlPane)
  }

}
