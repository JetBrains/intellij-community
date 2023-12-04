// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui

import com.intellij.codeEditor.printing.HTMLTextPainter
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.HintHint
import com.intellij.ui.JBColor
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jsoup.Jsoup
import java.awt.Color
import java.awt.Point
import java.io.IOException
import java.io.StringReader
import javax.swing.JEditorPane
import javax.swing.text.html.HTMLEditorKit

open class DescriptionEditorPane : JEditorPane(UIUtil.HTML_MIME, EMPTY_HTML) {

  init {
    isEditable = false
    isOpaque = false
    editorKit = HTMLEditorKitBuilder().withGapsBetweenParagraphs().withoutContentCss().build()
    val css = (this.editorKit as HTMLEditorKit).styleSheet
    css.addRule("a {overflow-wrap: anywhere;}")
    css.addRule("pre {padding:10px;}")
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
fun JEditorPane.readHTML(text: String) {
  val document = Jsoup.parse(text)

  for (pre in document.select("pre")) {
    if ("editor-background" !in pre.classNames()) pre.addClass("editor-background")
  }

  try {
    read(StringReader(document.html()), null)
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
    val psiFileFactory = PsiFileFactory.getInstance(defaultProject)

    val defaultFile = psiFileFactory.createFileFromText(PlainTextLanguage.INSTANCE, "")
    val content = codeSnippet.wholeText()
      .trimIndent()
      .trimEnd()
      .replaceIndent("  ")
    var snippet: String

    try {
      val file = psiFileFactory.createFileFromText(lang, "") ?: defaultFile
      snippet = HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(file, content)
    } catch (e: IllegalStateException) {
      snippet = HTMLTextPainter.convertCodeFragmentToHTMLFragmentWithInlineStyles(defaultFile, content)
    }

    codeSnippet.parent()?.html(
      snippet.removePrefix("<pre>").removeSuffix("</pre>").trimMargin()
    )
  }

  document.select("pre").forEach { it.addClass("editor-background") }

  try {
    read(StringReader(document.html()), null)
  }
  catch (e: IOException) {
    throw RuntimeException(e)
  }
}

@ApiStatus.ScheduledForRemoval
@Deprecated(message = "HTMl conversion is handled in JEditorPane.readHTML")
fun JEditorPane.toHTML(text: @Nls String?, miniFontSize: Boolean): String {
  val hintHint = HintHint(this, Point(0, 0))
  hintHint.setFont(if (miniFontSize) UIUtil.getLabelFont(UIUtil.FontSize.SMALL) else StartupUiUtil.labelFont)
  return HintUtil.prepareHintText(text!!, hintHint)
}