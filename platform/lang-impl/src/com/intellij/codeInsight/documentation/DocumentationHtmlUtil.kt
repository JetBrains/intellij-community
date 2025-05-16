// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.module.UnknownModuleType
import com.intellij.ui.ColorUtil
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.SmartList
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StyleSheetUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jsoup.nodes.*
import org.jsoup.select.QueryParser
import java.util.*
import java.util.function.Function
import javax.swing.Icon
import javax.swing.text.html.StyleSheet

@ApiStatus.Internal
object DocumentationHtmlUtil {

  @JvmStatic
  val settingsButtonPadding: Int get() = 9

  // hand-picked padding for HTML layout
  @JvmStatic
  val contentOuterPadding: Int get() = 14

  // Should be minimum 2 to compensate mandatory table border width of 2
  @JvmStatic
  val contentInnerPadding: Int get() = 2

  @JvmStatic
  val spaceBeforeParagraph: Int get() = JBHtmlPaneStyleConfiguration.defaultSpaceBeforeParagraph

  @JvmStatic
  val spaceAfterParagraph: Int get() = JBHtmlPaneStyleConfiguration.defaultSpaceAfterParagraph

  @JvmStatic
  val docPopupPreferredMinWidth: Int get() = 300

  @JvmStatic
  val docPopupPreferredMaxWidth: Int get() = 500

  @JvmStatic
  val docPopupMinWidth: Int get() = 300

  @JvmStatic
  val docPopupMaxWidth: Int get() = 900

  @JvmStatic
  val docPopupMaxHeight: Int get() = 500

  @JvmStatic
  val lookupDocPopupWidth: Int get() = 450

  @JvmStatic
  val lookupDocPopupMinHeight: Int get() = 300

  @JvmStatic
  fun getModuleIconResolver(baseIconResolver: Function<in String?, out Icon?>): (String) -> Icon? = { key: String ->
    baseIconResolver.apply(key)
    ?: ModuleTypeManager.getInstance().findByID(key)
      .takeIf { it !is UnknownModuleType }
      ?.icon
  }
  @JvmStatic
  fun getDocumentationPaneAdditionalCssRules(): StyleSheet =
    getDocumentationPaneAdditionalCssRules { JBUIScale.scale(it) }

  @JvmStatic
  fun getDocumentationPaneAdditionalCssRules(scaleFunction: Function<Int, Int>): StyleSheet {
    val linkColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Link.Foreground.ENABLED)
    val sectionColor = ColorUtil.toHtmlColor(JBUI.CurrentTheme.Tooltip.grayedForeground())

    // When updating styles here, consider updating styles in DocRenderer#getStyleSheet
    val contentOuterPadding = scaleFunction.apply(contentOuterPadding)
    val beforeSpacing = scaleFunction.apply(spaceBeforeParagraph)
    val afterSpacing = scaleFunction.apply(spaceAfterParagraph)
    val contentInnerPadding = scaleFunction.apply(contentInnerPadding)

    @Suppress("CssUnusedSymbol")
    @Language("CSS")
    val result = """
        html { padding: 0 ${contentOuterPadding}px 0 ${contentOuterPadding}px; margin: 0 }
        body { padding: 0; margin: 0; overflow-wrap: anywhere;}
        pre  { white-space: pre-wrap; }
        a { color: $linkColor; text-decoration: none;}
        .$CLASS_DEFINITION {    
          padding: ${beforeSpacing}px ${contentInnerPadding}px ${afterSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_DEFINITION pre { 
          margin: 0; padding: 0;
        }
        .$CLASS_CONTENT {
          padding: 0 ${contentInnerPadding}px 0px ${contentInnerPadding}px;
          max-width: 100%;
        }
        .$CLASS_BOTTOM, .$CLASS_DOWNLOAD_DOCUMENTATION, .$CLASS_TOP { 
          padding: ${beforeSpacing}px ${contentInnerPadding}px ${afterSpacing}px ${contentInnerPadding}px;
        }
        .$CLASS_SECTIONS { padding: 0 ${contentInnerPadding - 2}px 0 ${contentInnerPadding - 2}px 0; border-spacing: 0; }
        .$CLASS_SECTION { color: $sectionColor; padding-right: 4px; white-space: nowrap; }
      """.trimIndent() + DocumentationCssProvider.EP_NAME.extensionList.joinToString(separator = "\n", prefix = "\n") {
        it.generateCss(scaleFunction, false)
      }
    return StyleSheetUtil.loadStyleSheet(result)
  }

  @JvmStatic
  internal fun removeEmptySections(document: Document) {
    document.select(sectionsClassQuery).forEach { sections ->
      if (sections.childNodes().all { node -> node is Comment || node is TextNode && node.isBlank }) {
        sections.remove()
      }
    }
  }

  @JvmStatic
  internal fun addExternalLinkIcons(document: Document) {
    document.select(aElementQuery).forEach { a ->
      if (a.attribute("href")?.value?.startsWith("http") == true && a.select(externalLinkIconQuery).isEmpty()) {
        Element("icon").attr("src", "AllIcons.Ide.External_link_arrow")
          .appendTo(a)
      }
    }
  }

  @JvmStatic
  internal fun addParagraphsIfNeeded(document: Document, selector: String) {
    document.select(selector).forEach { element ->
      var child = element.firstChild()
      val toWrap = SmartList<Node>()
      while (child != null && !isBlockElement(child)) {
        if (child !is Comment) {
          toWrap.add(child)
        }
        child = child.nextSibling()
      }
      if (!toWrap.isEmpty() && !toWrap.all { n -> n is TextNode && n.isBlank }) {
        val para = Element("p")
        para.insertChildren(0, toWrap)
        element.insertChildren(0, para)
      }
    }
  }

  private val sectionsClassQuery = QueryParser.parse(".$CLASS_SECTIONS")
  private val aElementQuery = QueryParser.parse("a")
  private val externalLinkIconQuery = QueryParser.parse("icon[src=AllIcons.Ide.External_link_arrow]")

  private fun isBlockElement(node: Node): Boolean {
    if (node is Element) {
      val tagName = node.tagName().lowercase(Locale.US)
      return tagName == "p"
             || tagName == "div"
             || tagName == "pre"
             || tagName == "table"
             || tagName == "blockquote"
             || tagName == "ol"
             || tagName == "ul"
             || tagName == "dl"
             || (tagName.startsWith("h") && tagName.length == 2 && Character.isDigit(tagName[1]))
    }
    return false
  }
}
