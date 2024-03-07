// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.ide.ui.text.ShortcutsRenderingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.editor.impl.EditorCssFontResolver
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_PREFIX
import com.intellij.ui.components.JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_SUFFIX
import com.intellij.ui.components.JBHtmlPaneStyleSheetRulesProvider.getStyleSheet
import com.intellij.util.containers.CollectionFactory
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
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeEvent
import javax.swing.JEditorPane
import javax.swing.KeyStroke
import javax.swing.text.Document
import javax.swing.text.EditorKit
import javax.swing.text.StyledDocument
import javax.swing.text.View
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

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
 *
 */
@Suppress("LeakingThis")
open class JBHtmlPane(
  private val myStyleConfiguration: JBHtmlPaneStyleConfiguration,
  private val myPaneConfiguration: JBHtmlPaneConfiguration
) : JEditorPane(), Disposable {


  private var myText: @Nls String = "" // getText() surprisingly crashes…, let's cache the text
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
      },
      ExtendableHTMLViewFactory.Extensions.BLOCK_HR_SUPPORT
    )

    val editorKit = HTMLEditorKitBuilder()
      .replaceViewFactoryExtensions(*extensions.toTypedArray())
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

  override fun getText(): @Nls String {
    return myText
  }

  override fun setText(t: @Nls String?) {
    myText = t?.let { HtmlEditorPaneInputTranspiler(it).process() } ?: ""
    super.setText(myText)
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
    newStyleSheet.addStyleSheet(getStyleSheet(background, myStyleConfiguration))
    myPaneConfiguration.customStyleSheetProvider(background)?.let {
      newStyleSheet.addStyleSheet(it)
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

  /**
   * Transpiler performs some simple lexing to understand where are tags, attributes and text.
   *
   * For performance reasons, all the following actions are applied in a single run:
   * - Add `<wbr>` after `.` if surrounded by letters
   * - Add `<wbr>` after `]`, `)` or `/` followed by a char or digit
   * - Remove empty <p> before some tags - workaround for Swing html renderer not removing empty paragraphs before non-inline tags
   * - Replace `<blockquote>\\s*<pre>` with [JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_PREFIX]
   * - Replace `</pre>\\s*</blockquote>` with [JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_SUFFIX]
   * - Replace `<pre><code>` with [JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_PREFIX]
   * - Replace `</code></pre>` with [JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_SUFFIX]
   * - Expand `<shortcut raw|actionId="*"/>` tag into a sequence of `<kbd>` tags
   */
  private class HtmlEditorPaneInputTranspiler(@Nls text: String) {
    private val codePoints = text.codePoints().iterator()
    private val result = StringBuilder(text.length + 50)
    private var codePoint = codePoints.nextInt()
    private var openingTag = false
    private val tagStart = StringBuilder()
    private val tagName = StringBuilder()
    private val tagBuffer = StringBuilder()

    @Nls
    fun process(): String {
      if (!codePoints.hasNext()) return ""


      while (codePoint >= 0) {
        when {
          // break after dot if surrounded by letters
          Character.isLetter(codePoint) -> {
            next()
            if (codePoint == '.'.code) {
              next()
              if (Character.isLetter(codePoint)) {
                result.append("<wbr>")
              }
            }
          }
          // break after ], ) or / followed by a char or digit
          codePoint == ')'.code || codePoint == ']'.code || codePoint == '/'.code -> {
            next()
            if (Character.isLetterOrDigit(codePoint)) {
              result.append("<wbr>")
            }
          }
          // process tags
          codePoint == '<'.code -> {
            readTagStart()
            if (tagName.isEmpty()) {
              result.append(tagStart)
              continue
            }
            if (tagName.contentEquals("style", true)
                || tagName.contentEquals("title", true)
                || tagName.contentEquals("script", true)
                || tagName.contentEquals("textarea", true)) {
              result.append(tagStart)
              skipUntilTagClose(tagName.toString(), result)
            }
            else
              handleTag()
          }
          else -> next()
        }
      }

      @Suppress("HardCodedStringLiteral")
      return result.toString()
    }

    private fun next(builder: StringBuilder = result) {
      builder.appendCodePoint(codePoint)
      codePoint = if (codePoints.hasNext())
        codePoints.nextInt()
      else
        -1
    }

    private fun skipUntilTagClose(curTag: String, builder: StringBuilder) {
      do {
        if (codePoint == '<'.code) {
          readTagStart()
          builder.append(tagStart)
          if (tagName.contentEquals(curTag, true) && !openingTag) {
            skipUntil(">", builder)
            break
          }
        }
        else next(builder)
      }
      while (codePoint >= 0)
    }

    private fun readTagStart() {
      assert(codePoint == '<'.code)
      tagStart.clear()
      tagName.clear()
      next(tagStart)
      if (codePoint == '/'.code) {
        openingTag = false
        next(tagStart)
      }
      else if (codePoint == '!'.code) {
        next(tagStart)
        if (consume("--", tagStart)) {
          skipUntil("->", tagStart)
        }
      }
      else {
        openingTag = true
      }
      if (!Character.isLetter(codePoint))
        return
      while (Character.isLetterOrDigit(codePoint) || codePoint == '-'.code) {
        next(tagName)
      }
      tagStart.append(tagName)
    }

    private fun consume(text: String, builder: StringBuilder): Boolean {
      for (c in text) {
        if (codePoint != c.code) return false
        next(builder)
      }
      return true
    }

    private fun skipUntil(text: String, builder: StringBuilder) {
      loop@ while (codePoint >= 0) {
        for (i in text.indices) {
          val c = text[i]
          if (codePoint != c.code) {
            if (i == 0) next(builder)
            continue@loop
          }
          next(builder)
        }
        return
      }
    }

    private fun skipToTagEnd(builder: StringBuilder) {
      while (codePoint >= 0) {
        when (codePoint) {
          '/'.code -> {
            openingTag = false
            next(builder)
          }
          '>'.code -> {
            next(builder)
            break
          }
          '\''.code, '"'.code -> {
            val quoteStyle = codePoint
            next(builder)
            while (codePoint >= 0) {
              when (codePoint) {
                '\\'.code -> {
                  next(builder)
                  if (codePoint >= 0)
                    next(builder)
                }
                quoteStyle -> {
                  next(builder)
                  break
                }
                else -> next(builder)
              }
            }
          }
          else -> next(builder)
        }
      }
    }

    private fun readAttributes(builder: StringBuilder): Map<String, String> {
      val result = mutableMapOf<String, String>()
      var attributeName: String? = null
      var nextIsValue = false
      val text = StringBuilder()
      while (codePoint >= 0) {
        when (codePoint) {
          '/'.code -> {
            openingTag = false
            next(builder)
          }
          '>'.code -> {
            if (text.isNotEmpty()) {
              builder.append(text)
              result[text.toString()] = text.toString()
            }
            else if (attributeName != null) {
              result[attributeName] = attributeName
            }
            next(builder)
            break
          }
          '\''.code, '"'.code -> {
            val quoteStyle = codePoint
            next(builder)
            while (codePoint >= 0) {
              when (codePoint) {
                '\\'.code -> {
                  next(text)
                  if (codePoint >= 0)
                    next(text)
                }
                quoteStyle -> {
                  builder.append(text)
                  next(builder)
                  break
                }
                else -> next(text)
              }
            }
            if (nextIsValue) {
              result[attributeName!!] = text.toString()
              attributeName = null
              nextIsValue = false
            }
            text.clear()
          }
          else -> if (codePoint == '='.code || Character.isWhitespace(codePoint)) {
            if (text.isNotEmpty()) {
              if (codePoint != '='.code && nextIsValue) {
                result[attributeName!!] = text.toString()
                attributeName = null
              }
              else {
                attributeName = StringUtil.toLowerCase(text.toString())
              }
              builder.append(text)
              text.clear()
            }
            nextIsValue = codePoint == '='.code && !attributeName.isNullOrEmpty()
            next(builder)
          }
          else
            next(text)
        }
      }
      return result
    }

    private fun handleTag() {
      val isOpeningTag = openingTag
      if (isOpeningTag && tagName.contentEquals("shortcut", true)) {
        handleShortcutTag()
        return
      }
      val isP = tagName.contentEquals("p", true)
      val isPre = tagName.contentEquals("pre", true)
      val isCode = tagName.contentEquals("code", true)
      val isBlockquote = tagName.contentEquals("blockquote", true)
      if (!isP && !isPre && !(isCode && !isOpeningTag) && !(isBlockquote && isOpeningTag)) {
        result.append(tagStart)
        skipToTagEnd(result)
        return
      }

      tagBuffer.clear()
      tagBuffer.append(tagStart)
      skipToTagEnd(tagBuffer)
      if (isP || isBlockquote || (isPre && !isOpeningTag)) {
        // Skip whitespace
        while (codePoint >= 0 && Character.isWhitespace(codePoint)) {
          next(tagBuffer)
        }
      }
      if (codePoint == '<'.code) {
        readTagStart()
        if (isP) {
          // Remove empty <p> before some tags - workaround for Swing html renderer not removing empty paragraphs before non-inline tags
          if (tagName !in dropPrecedingEmptyParagraphTags) {
            result.append(tagBuffer)
          }
          handleTag()
        }
        else {
          // Replace <blockquote>\\s*<pre> with JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_PREFIX
          // Replace </pre>\\s*</blockquote> with JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_SUFFIX
          // Replace <pre><code> with JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_PREFIX
          // Replace </code></pre> with JBHtmlPaneStyleSheetRulesProvider.CODE_BLOCK_SUFFIX
          val nextTag = if (isPre) {
            if (isOpeningTag) "code" else "blockquote"
          }
          else "pre"
          if (tagName.contentEquals(nextTag, true) && (isOpeningTag == openingTag)) {
            skipToTagEnd(tagBuffer)
            if (isCode || (isPre && !isOpeningTag)) {
              // trim trailing whitespace
              result.setLength(result.indexOfLast { !Character.isWhitespace(it) } + 1)
            }
            result.append(if (isOpeningTag) CODE_BLOCK_PREFIX else CODE_BLOCK_SUFFIX)
          }
          else {
            result.append(tagBuffer)
            handleTag()
          }
        }
      }
      else {
        result.append(tagBuffer)
      }
    }

    private fun handleShortcutTag() {
      tagBuffer.clear()
      tagBuffer.append(tagStart)
      val attributes = readAttributes(tagBuffer)
      val actionId = attributes["actionid"]
      val raw = attributes["raw"]

      if (openingTag)
        skipUntilTagClose("shortcut", tagBuffer)

      if (actionId != null || raw != null) {
        val shortcutData =
          if (actionId != null)
            ShortcutsRenderingUtil.getShortcutByActionId(actionId)
              ?.let { ShortcutsRenderingUtil.getKeyboardShortcutData(it) }?.first
            ?: ShortcutsRenderingUtil.getGotoActionData(actionId, false)
              .takeIf { ActionManager.getInstance().getAction(actionId) != null }
              ?.first
          else
            KeyStroke.getKeyStroke(raw)
              ?.let { ShortcutsRenderingUtil.getKeyStrokeData(it) }
              ?.first
        if (shortcutData != null) {
          shortcutData
            .splitToSequence(ShortcutsRenderingUtil.SHORTCUT_PART_SEPARATOR)
            .joinToString(StringUtil.NON_BREAK_SPACE) { "<kbd>$it</kbd>" }
            .let(result::append)
        }
        else {
          result.append("<kbd>${actionId ?: raw}</kbd>")
        }
      }
      else {
        result.append(tagBuffer)
      }
    }

  }

  companion object {
    private val dropPrecedingEmptyParagraphTags = CollectionFactory.createCharSequenceSet(false).also {
      it.addAll(listOf("ul", "ol", "h1", "h2", "h3", "h4", "h5", "h6", "p", "tr", "td"))
    }
  }
}
