// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBHtmlPaneStyleConfiguration
import org.jetbrains.annotations.Nls
import org.jsoup.Jsoup
import java.awt.Color
import java.io.IOException
import javax.swing.JEditorPane

/**
 * A custom editor pane used for displaying descriptions in HTML format.
 *
 * @see JBHtmlPane
 */
open class DescriptionEditorPane : JBHtmlPane(
  JBHtmlPaneStyleConfiguration(),
  JBHtmlPaneConfiguration {
    customStyleSheet("pre {white-space: pre-wrap;} code, pre, a {overflow-wrap: anywhere;}")
  }) {

  init {
    isEditable = false
    isOpaque = false
  }

  override fun getBackground(): Color = JBColor.PanelBackground

  companion object {
    const val EMPTY_HTML: String = "<html><body></body></html>"
  }

}

/**
 * Parses the input HTML [text] and displays its content.
 *
 * @param text The HTML content as a [String] to be displayed in the [JEditorPane].
 * @throws RuntimeException if an exception occurs while parsing or displaying the HTML content.
 */
fun JEditorPane.readHTML(text: @Nls String) {
  try {
    setText(text)
  }
  catch (e: IOException) {
    throw RuntimeException(e)
  }
}

/**
 * Parses the input HTML [text] and displays its content.
 * Adds highlighting for code fragments wrapped in `<pre><code>` elements.
 * Specify the `lang` parameter on `code` elements to override the default language.
 * If the language is not provided or unrecognized, it will default to plain text.
 *
 * @param text The HTML content as a [String] to be displayed in the [JEditorPane].
 * @param language The optional ID of the programming language to be used in code fragments.
 * @throws RuntimeException if an exception occurs while parsing or displaying the HTML content.
 */
fun JEditorPane.readHTMLWithCodeHighlighting(text: String, language: String?) {
  var lang = Language.findLanguageByID(language) ?: PlainTextLanguage.INSTANCE
  val document = Jsoup.parse(text)

  // IDEA-318323
  if (text.contains("<body>\n<p>")) {
    document.select("body > :first-child").first()?.tagName("div")
  }

  document.select("pre code").forEach { codeSnippet ->
    if (codeSnippet.hasAttr("lang")) lang = LanguageUtil.findRegisteredLanguage(codeSnippet.attr("lang")) ?: lang
    val defaultProject = DefaultProjectFactory.getInstance().defaultProject

    val styledBlock = Jsoup.parse(QuickDocHighlightingHelper.getStyledCodeBlock(defaultProject, lang, codeSnippet.wholeText()))
    val styledHtml = styledBlock.select("pre code").first()?.html()
    if (styledHtml != null) codeSnippet.html(styledHtml)
  }

  try {
    @Suppress("HardCodedStringLiteral")
    setText(document.html())
  }
  catch (e: IOException) {
    throw RuntimeException(e)
  }
}