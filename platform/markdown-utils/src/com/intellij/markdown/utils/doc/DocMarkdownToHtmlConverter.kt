// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils.doc

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes.Companion.CODE_FENCE_CONTENT
import org.intellij.markdown.MarkdownTokenTypes.Companion.HTML_TAG
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.commonmark.CommonMarkMarkerProcessor
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.DUMMY_ATTRIBUTES_CUSTOMIZER
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.HtmlGenerator.DefaultTagRenderer
import org.intellij.markdown.html.HtmlGenerator.HtmlGeneratingVisitor
import org.intellij.markdown.parser.*
import org.intellij.markdown.parser.constraints.CommonMarkdownConstraints
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.markerblocks.providers.HtmlBlockProvider
import org.jetbrains.annotations.Contract
import org.jetbrains.annotations.Nls
import java.net.URI
import java.util.regex.Pattern

object DocMarkdownToHtmlConverter {
  private val LOG = Logger.getInstance(DocMarkdownToHtmlConverter::class.java)

  private val TAG_START_OR_CLOSE_PATTERN: Pattern = Pattern.compile("(<)/?(\\w+)[> ]")
  private val TAG_PATTERN: Pattern = Pattern.compile("^</?([a-z][a-z-_0-9]*)[^>]*>$", Pattern.CASE_INSENSITIVE)
  private val SPLIT_BY_LINE_PATTERN: Pattern = Pattern.compile("\n|\r|\r\n")
  private const val FENCED_CODE_BLOCK = "```"

  private val HTML_DOC_SUBSTITUTIONS: Map<String, String> = mapOf(
    "<pre><code>" to "<pre>",
    "</code></pre>" to "</pre>",
    "<em>" to "<i>",
    "</em>" to "</i>",
    "<strong>" to "<b>",
    "</strong>" to "</b>",
    ": //" to "://", // Fix URL
    "<p></p><pre>" to "<pre>",
    "</p><pre>" to "<pre>",
    "</p>" to "",
    "<br  />" to "",
  )


  internal val ACCEPTABLE_BLOCK_TAGS: MutableSet<CharSequence> = CollectionFactory.createCharSequenceSet(false)
    .apply {
      addAll(listOf( // Text content
        "blockquote", "dd", "dl", "dt",
        "hr", "li", "ol", "ul", "pre", "p",  // Table,

        "caption", "col", "colgroup", "table", "tbody", "td", "tfoot", "th", "thead", "tr"
      ))
    }

  private val ACCEPTABLE_TAGS: Set<CharSequence> = CollectionFactory.createCharSequenceSet(false)
    .apply {
      addAll(ACCEPTABLE_BLOCK_TAGS)
      addAll(listOf( // Content sectioning
        "h1", "h2", "h3", "h4", "h5", "h6",  // Inline text semantic
        "a", "b", "br", "code", "em", "i", "s", "span", "strong", "u", "wbr",  // Image and multimedia
        "img",  // Svg and math
        "svg",  // Obsolete
        "tt"
      ))
    }

  @Contract(pure = true)
  @JvmStatic
  fun convert(markdownText: String): String {
    val lines = SPLIT_BY_LINE_PATTERN.split(markdownText)
    val processedLines = ArrayList<String>(lines.size)
    var isInCode = false
    var isInTable = false
    var tableFormats: List<String>? = null
    for (i in lines.indices) {
      val line = lines[i]
      var processedLine = StringUtil.trimTrailing(line)
      if (processedLine.matches("\\s+```.*".toRegex())) {
        processedLine = processedLine.trim { it <= ' ' }
      }

      val count = StringUtil.getOccurrenceCount(processedLine, FENCED_CODE_BLOCK)
      if (count > 0) {
        isInCode = if (count % 2 == 0) isInCode else !isInCode
      }
      else {
        // TODO merge custom table generation code with Markdown parser
        val tableDelimiterIndex = processedLine.indexOf('|')
        if (tableDelimiterIndex != -1) {
          if (!isInTable) {
            if (i + 1 < lines.size) {
              tableFormats = parseTableFormats(splitTableCols(lines[i + 1]))
            }
          }
          // create table only if we've successfully read the formats line
          if (!ContainerUtil.isEmpty<String?>(tableFormats)) {
            val parts = splitTableCols(processedLine)
            if (isTableHeaderSeparator(parts)) continue
            processedLine = getProcessedRow(isInTable, parts, tableFormats)
            if (!isInTable) processedLine = "<table style=\"border: 0px;\" cellspacing=\"0\">$processedLine"
            isInTable = true
          }
        }
        else {
          if (isInTable) processedLine += "</table>"
          isInTable = false
          tableFormats = null
        }
        processedLine = if (isInCode) processedLine else StringUtil.trimLeading(processedLine)
      }
      processedLines.add(processedLine)
    }
    var normalizedMarkdown = StringUtil.join(processedLines, "\n")
    if (isInTable) normalizedMarkdown += "</table>" //NON-NLS

    var html = performConversion(normalizedMarkdown)
    if (html == null) {
      html = replaceProhibitedTags(convertNewLinePlaceholdersToTags(markdownText), ContainerUtil.emptyList())
    }
    return adjustHtml(html)
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

  private fun getProcessedRow(isInTable: Boolean,
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
      resultBuilder.append(performConversion(parts[i].trim { it <= ' ' }))
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

  private fun performConversion(text: @Nls String): @NlsSafe String? {
    try {
      val flavour = DocumentationFlavourDescriptor()
      val parsedTree = MarkdownParser(flavour).parse(embeddedHtmlType, text, true)
      return HtmlGenerator(text, parsedTree, flavour, false)
        .generateHtml(DocumentationTagRenderer(text))
    }
    catch (e: Exception) {
      LOG.warn(e.message, e)
      return null
    }
  }

  private fun replaceProhibitedTags(line: String, skipRanges: List<TextRange>): String {
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
      str = str.replace(key, value)
    }
    return str.trim { it <= ' ' }
  }

  private val border: String
    get() = "margin: 0; border: 1px solid; border-color: #" + ColorUtil
      .toHex(UIUtil.getTooltipSeparatorColor()) + "; border-spacing: 0; border-collapse: collapse;vertical-align: baseline;"

  private class DocumentationMarkerProcessor(productionHolder: ProductionHolder,
                                             constraintsBase: CommonMarkdownConstraints) : CommonMarkMarkerProcessor(productionHolder,
                                                                                                                     constraintsBase) {
    override fun getMarkerBlockProviders(): List<MarkerBlockProvider<StateInfo>> =
      super.getMarkerBlockProviders().filter { it !is HtmlBlockProvider } + DocHtmlBlockProvider()
  }

  private class DocumentationFlavourDescriptor : GFMFlavourDescriptor() {
    override val markerProcessorFactory: MarkerProcessorFactory
      get() = object : MarkerProcessorFactory {
        override fun createMarkerProcessor(productionHolder: ProductionHolder): MarkerProcessor<*> =
          DocumentationMarkerProcessor(productionHolder, GFMConstraints.BASE)
      }

    override fun createHtmlGeneratingProviders(linkMap: LinkMap, baseURI: URI?): Map<IElementType, GeneratingProvider> {
      val result = HashMap(super.createHtmlGeneratingProviders(linkMap, baseURI))
      result[HTML_TAG] = SanitizingTagGeneratingProvider()
      return result
    }
  }

  private class SanitizingTagGeneratingProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGeneratingVisitor, text: String, node: ASTNode) {
      val nodeText = node.getTextInNode(text)
      val matcher = TAG_PATTERN.matcher(nodeText)
      if (matcher.matches()) {
        val tagName = matcher.group(1)
        if (StringUtil.equalsIgnoreCase(tagName, "div")) {
          visitor.consumeHtml(nodeText.subSequence(0, matcher.start(1)))
          visitor.consumeHtml("span")
          visitor.consumeHtml(nodeText.subSequence(matcher.end(1), nodeText.length))
          return
        }
        if (ACCEPTABLE_TAGS.contains(tagName)) {
          visitor.consumeHtml(nodeText)
          return
        }
      }
      visitor.consumeHtml(StringUtil.escapeXmlEntities(nodeText.toString()))
    }
  }

  private class DocumentationTagRenderer(private val wholeText: String)
    : DefaultTagRenderer(DUMMY_ATTRIBUTES_CUSTOMIZER, false) {

    override fun openTag(node: ASTNode, tagName: CharSequence,
                         vararg attributes: CharSequence?,
                         autoClose: Boolean): CharSequence {
      if (tagName.contentEquals("p", true)) {
        val first = node.children.firstOrNull()
        if (first != null && first.type === HTML_TAG) {
          val text = first.getTextInNode(wholeText)
          val matcher = TAG_PATTERN.matcher(text)
          if (matcher.matches()) {
            val nestedTag = matcher.group(1)
            if (ACCEPTABLE_BLOCK_TAGS.contains(nestedTag)) {
              return ""
            }
          }
        }
      }
      if (tagName.contentEquals("code", true) && node.type === CODE_FENCE_CONTENT) {
        return ""
      }
      return super.openTag(node, convertTag(tagName), *attributes, autoClose = autoClose)
    }

    override fun closeTag(tagName: CharSequence): CharSequence {
      if (tagName.contentEquals("p", true)) return ""
      return super.closeTag(convertTag(tagName))
    }

    private fun convertTag(tagName: CharSequence): CharSequence {
      if (tagName.contentEquals("strong", true)) {
        return "b"
      }
      else if (tagName.contentEquals("em", true)) {
        return "i"
      }
      return tagName
    }
  }
}
