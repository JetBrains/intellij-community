// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeServer

/**
 * A highlight of the editor, or an expected one.
 * [kind] is the severity of an error or a warning, and the external name of the text attributes key of any other highlight.
 * An expected highlight without `descr` in the markup has no [description].
 */
internal data class Highlight(val kind: String, val startOffset: Int, val endOffset: Int, val description: String?) {
  fun matches(actual: Highlight): Boolean =
    kind == actual.kind && startOffset == actual.startOffset && endOffset == actual.endOffset &&
    (description == null || description == actual.description)
}

internal class HighlightingMarkup(val text: String, val highlights: List<Highlight>)

/**
 * Reads the markup of the highlighting tests: `<error descr="message">text</error>`, `<warning ...>`, `<EOLError ...>`
 * for an error that the editor shows after the end of a line, and `<text_attr textAttributesKey="KEY">text</text_attr>`.
 * Returns the text without the tags and the highlights.
 */
internal fun parseHighlightingMarkup(markup: String): HighlightingMarkup {
  val text = StringBuilder()
  val highlights = mutableListOf<Highlight>()
  val openTags = ArrayDeque<Triple<String, Int, String?>>()
  var position = 0
  for (tag in TAG.findAll(markup)) {
    text.append(markup, position, tag.range.first)
    position = tag.range.last + 1
    val name = tag.groupValues[2]
    if (tag.groupValues[1].isEmpty()) {
      openTags.addLast(Triple(name, text.length, tag.groups[3]?.value))
    }
    else {
      val (openName, startOffset, description) = openTags.removeLast()
      check(openName == name) { "</$name> closes <$openName> in: $markup" }
      highlights.add(if (name == TEXT_ATTRIBUTES_TAG) Highlight(checkNotNull(description), startOffset, text.length, null)
                     else Highlight(SEVERITIES.getValue(name), startOffset, text.length, description))
    }
  }
  check(openTags.isEmpty()) { "Unclosed tags in: $markup" }
  text.append(markup, position, markup.length)
  return HighlightingMarkup(text.toString(), highlights)
}

private val SEVERITIES = mapOf("error" to "ERROR", "EOLError" to "ERROR", "warning" to "WARNING")

private const val TEXT_ATTRIBUTES_TAG = "text_attr"

private val TAG = Regex("""<(/?)(error|EOLError|warning|text_attr)(?: (?:descr|textAttributesKey)="([^"]*)")?>""")
