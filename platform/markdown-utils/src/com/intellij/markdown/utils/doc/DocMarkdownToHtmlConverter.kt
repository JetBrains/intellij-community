// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc

import com.intellij.lang.Language
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.lang.documentation.QuickDocHighlightingHelper
import com.intellij.markdown.utils.doc.impl.DocFlavourDescriptor
import com.intellij.markdown.utils.doc.impl.DocTagRenderer
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.ui.ColorUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import java.util.regex.Pattern

/**
 * [DocMarkdownToHtmlConverter] handles conversion of Markdown text to HTML, which is intended
 * to be displayed in Quick Doc popup, or inline in an editor.
 */
object DocMarkdownToHtmlConverter {
  private val LOG = Logger.getInstance(DocMarkdownToHtmlConverter::class.java)
  private val TAG_START_OR_CLOSE_PATTERN: Pattern = Pattern.compile("(<)/?(\\w+)[> ]")
  internal val TAG_PATTERN: Pattern = Pattern.compile("^</?([a-z][a-z-_0-9]*)[^>]*>$", Pattern.CASE_INSENSITIVE)
  private val FENCE_PATTERN = "\\s+```.*".toRegex()
  private const val FENCED_CODE_BLOCK = "```"

  private val HTML_DOC_SUBSTITUTIONS: Map<String, String> = mapOf(
    "<em>" to "<i>",
    "</em>" to "</i>",
    "<strong>" to "<b>",
    "</strong>" to "</b>",
    ": //" to "://", // Fix URL
    "<p></p>" to "",
    "</p>" to "",
    "<br  />" to "",
  )


  internal val ACCEPTABLE_BLOCK_TAGS: MutableSet<CharSequence> = CollectionFactory.createCharSequenceSet(false)
    .apply {
      addAll(listOf( // Text content
        "blockquote", "dd", "dl", "dt",
        "hr", "li", "ol", "ul", "pre", "p",  // Table,

        "caption", "col", "colgroup", "table", "tbody", "td", "tfoot", "th", "thead", "tr",

        "details", "summary"
      ))
    }

  internal val ACCEPTABLE_TAGS: Set<CharSequence> = CollectionFactory.createCharSequenceSet(false)
    .apply {
      addAll(ACCEPTABLE_BLOCK_TAGS)
      addAll(listOf( // Content sectioning
        "h1", "h2", "h3", "h4", "h5", "h6",
        // Inline text semantic
        "a", "b", "br", "code", "em", "i", "s", "span", "strong", "u", "wbr", "kbd", "samp",
        // Image and multimedia
        "img",
        // Svg and math
        "svg",
        // Obsolete
        "tt",
        // special IJ tags
        "shortcut", "icon"
      ))
    }

  /**
   * Converts provided Markdown text to HTML. The results are intended to be used for Quick Documentation.
   * If [defaultLanguage] is provided, it will be used for syntax coloring of inline code and code blocks, if language specifier is missing.
   * Block and inline code syntax coloring is being done by [QuickDocHighlightingHelper], which honors [DocumentationSettings].
   * Conversion must be run within a Read Action as it might require to create intermediate [PsiFile] to highlight block of code,
   * or an inline code.
   */
  @Contract(pure = true)
  @JvmStatic
  @RequiresReadLock
  @JvmOverloads
  fun convert(project: Project, @Nls markdownText: String, defaultLanguage: Language? = null): @Nls String {
    val lines = markdownText.lines()
    val minCommonIndent =
      lines
        .filter(String::isNotBlank)
        .minOfOrNull { line -> line.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) line.length else it } }
      ?: 0
    val processedLines = ArrayList<String>(lines.size)
    var isInCode = false
    var isInTable = false
    var tableFormats: List<String>? = null
    for (i in lines.indices) {
      var processedLine = lines[i].let { if (it.length <= minCommonIndent) "" else it.substring(minCommonIndent) }
      processedLine = processedLine.trimEnd()
      val count = StringUtil.getOccurrenceCount(processedLine, FENCED_CODE_BLOCK)
      if (count > 0) {
        isInCode = if (count % 2 == 0) isInCode else !isInCode
        if (processedLine.matches(FENCE_PATTERN)) {
          processedLine = processedLine.trim { it <= ' ' }
        }
      }
      else if (!isInCode) {
        // TODO merge custom table generation code with Markdown parser
        val tableDelimiterIndex = processedLine.indexOf('|')
        if (tableDelimiterIndex != -1) {
          if (!isInTable) {
            if (i + 1 < lines.size) {
              tableFormats = parseTableFormats(splitTableCols(lines[i + 1]))
            }
          }
          // create table only if we've successfully read the formats line
          if (!ContainerUtil.isEmpty(tableFormats)) {
            val parts = splitTableCols(processedLine)
            if (isTableHeaderSeparator(parts)) continue
            processedLine = getProcessedRow(project, defaultLanguage, isInTable, parts, tableFormats)
            if (!isInTable) processedLine = "<table style=\"border: 0px;\" cellspacing=\"0\">$processedLine"
            isInTable = true
          }
        }
        else {
          if (isInTable) processedLine += "</table>"
          isInTable = false
          tableFormats = null
        }
      }
      processedLines.add(processedLine)
    }
    var normalizedMarkdown = StringUtil.join(processedLines, "\n")
    if (isInTable) normalizedMarkdown += "</table>" //NON-NLS

    return performConversion(project, defaultLanguage, normalizedMarkdown)?.trimEnd()
           ?: adjustHtml(replaceProhibitedTags(convertNewLinePlaceholdersToTags(markdownText), ContainerUtil.emptyList()))
  }

  private fun convertNewLinePlaceholdersToTags(generatedDoc: String): String {
    return StringUtil.replace(generatedDoc, "\n", "\n<p>")
  }

  private fun parseTableFormats(cols: List<String>): List<String>? {
    val formats = ArrayList<String>()
    for (col in cols) {
      if (!isHeaderSeparator(col)) return null
      formats.add(parseFormat(col.trim { it <= ' ' }))
    }
    return formats
  }

  private fun isTableHeaderSeparator(parts: List<String>): Boolean =
    parts.all { isHeaderSeparator(it) }

  private fun isHeaderSeparator(s: String): Boolean =
    s.trim { it <= ' ' }.removePrefix(":").removeSuffix(":").chars().allMatch { it == '-'.code }

  private fun splitTableCols(processedLine: String): List<String> {
    val parts = ArrayList(StringUtil.split(processedLine, "|"))
    if (parts.isEmpty()) return parts
    if (parts[0].isNullOrBlank())
      parts.removeAt(0)
    if (!parts.isEmpty() && parts[parts.size - 1].isNullOrBlank())
      parts.removeAt(parts.size - 1)
    return parts
  }

  private fun getProcessedRow(project: Project,
                              defaultLanguage: Language?,
                              isInTable: Boolean,
                              parts: List<String>,
                              tableFormats: List<String>?): String {
    val openingTagStart = if (isInTable)
      "<td style=\"$border\" "
    else
      "<th style=\"$border\" "
    val closingTag = if (isInTable) "</td>" else "</th>"
    val resultBuilder = StringBuilder("<tr style=\"$border\">$openingTagStart")
    resultBuilder.append("align=\"").append(getAlign(0, tableFormats)).append("\">")
    for (i in parts.indices) {
      if (i > 0) {
        resultBuilder.append(closingTag).append(openingTagStart).append("align=\"").append(getAlign(i, tableFormats)).append("\">")
      }
      resultBuilder.append(performConversion(project, defaultLanguage, parts[i].trim { it <= ' ' }))
    }
    resultBuilder.append(closingTag).append("</tr>")
    return resultBuilder.toString()
  }

  private fun getAlign(index: Int, formats: List<String>?): String {
    return if (formats == null || index >= formats.size) "left" else formats[index]
  }

  private fun parseFormat(format: String): String {
    if (format.length <= 1) return "left"
    val c0 = format[0]
    val cE = format[format.length - 1]
    return if (c0 == ':' && cE == ':') "center" else if (cE == ':') "right" else "left"
  }

  private val embeddedHtmlType = IElementType("ROOT")

  private fun performConversion(project: Project, defaultLanguage: Language?, text: @Nls String): @NlsSafe String? {
    try {
      val flavour = DocFlavourDescriptor(project, defaultLanguage)
      val parsedTree = MarkdownParser(flavour).parse(embeddedHtmlType, text, true)
      return HtmlGenerator(text, parsedTree, flavour, false)
        .generateHtml(DocTagRenderer(text))
    }
    catch (e: Exception) {
      if (e is ControlFlowException) throw e
      LOG.warn(e.message, e)
      return null
    }
  }

  private fun replaceProhibitedTags(line: String, skipRanges: List<TextRange>): @NlsSafe String {
    val matcher = TAG_START_OR_CLOSE_PATTERN.matcher(line)
    val builder = StringBuilder(line)

    var diff = 0
    l@ while (matcher.find()) {
      val tagName = matcher.group(2)

      if (ACCEPTABLE_TAGS.contains(tagName)) continue

      val startOfTag = matcher.start(2)
      for (range in skipRanges) {
        if (range.contains(startOfTag)) {
          continue@l
        }
      }

      val start = matcher.start(1) + diff
      if (StringUtil.toLowerCase(tagName) == "div") {
        val isOpenTag = !matcher.group(0).contains("/")
        val end = start + (if (isOpenTag) 5 else 6)
        val replacement = if (isOpenTag) "<span>" else "</span>"
        builder.replace(start, end, replacement)
        diff += 1
      }
      else {
        builder.replace(start, start + 1, "&lt;")
        diff += 3
      }
    }
    return builder.toString()
  }

  @Contract(pure = true)
  private fun adjustHtml(html: String): String {
    var str = html
    for ((key, value) in HTML_DOC_SUBSTITUTIONS) {
      if (str.indexOf(key) > 0) {
        str = str.replace(key, value)
      }
    }
    return str.trim { it <= ' ' }
  }

  private val border: String
    get() = "margin: 0; border: 1px solid; border-color: #" + ColorUtil
      .toHex(UIUtil.getTooltipSeparatorColor()) + "; border-spacing: 0; border-collapse: collapse;vertical-align: baseline;"

}
