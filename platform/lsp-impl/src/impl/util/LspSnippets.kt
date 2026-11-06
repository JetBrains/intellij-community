// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.util

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.Variable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/** A part of an LSP snippet (spec "Snippet Syntax"). */
internal sealed interface LspSnippetPart {
  data class Text(val value: String) : LspSnippetPart

  /** `$1`, `${1}`, `${1:placeholder}` or `${1|a,b|}`; [placeholder] is empty for a bare tab stop. */
  data class TabStop(val index: Int, val placeholder: List<LspSnippetPart> = emptyList(), val choices: List<String>? = null) : LspSnippetPart

  /** `$NAME`, `${NAME}` or `${NAME:default}`. */
  data class Variable(val name: String, val default: List<LspSnippetPart>? = null) : LspSnippetPart
}

/**
 * A recursive-descent parser of the snippet grammar: tab stops, nested placeholders, choices, variables, the escapes `\$`,
 * `\}`, `\\` (and `\,` `\|` in choices). A transform (`${1/re/fmt/opts}`) parses and is ignored. A construct that does not
 * parse is literal text, as in VS Code. An unclosed placeholder or default is its opening text followed by its parsed content,
 * as in VS Code, so the parser reads each character a bounded number of times.
 * A construct nested deeper than [MAX_NESTING] levels is text, so that a large snippet cannot overflow the stack.
 */
internal object LspSnippetParser {
  private const val MAX_NESTING = 100

  fun parse(text: String): List<LspSnippetPart> = Parser(text).parseAny(untilBrace = false)

  private class Parser(val s: String) {
    var i = 0
    var nesting = 0

    fun parseAny(untilBrace: Boolean): List<LspSnippetPart> {
      val parts = ArrayList<LspSnippetPart>()
      val text = StringBuilder()
      fun flush() {
        if (text.isNotEmpty()) parts += LspSnippetPart.Text(text.toString()).also { text.clear() }
      }
      while (i < s.length) {
        val c = s[i]
        when {
          c == '\\' && i + 1 < s.length && s[i + 1] in "$}\\" -> {
            text.append(s[i + 1]); i += 2
          }
          c == '}' && untilBrace -> break
          c == '$' -> {
            val start = i
            val construct = dollar()
            if (construct == null) {
              i = start + 1; text.append('$')
            }
            else {
              for (part in construct) {
                if (part is LspSnippetPart.Text) text.append(part.value) else { flush(); parts += part }
              }
            }
          }
          else -> {
            text.append(c); i++
          }
        }
      }
      flush()
      return parts
    }

    /**
     * At `$`; the parts of the construct, or null (position undefined) when the construct does not parse.
     * An unclosed `${1:` or `${NAME:` gives its opening text and its content.
     */
    private fun dollar(): List<LspSnippetPart>? {
      val start = i
      i++
      if (i >= s.length) return null
      val c = s[i]
      if (c.isDigit()) return listOf(LspSnippetPart.TabStop(int()))
      if (c.isNameStart()) return listOf(LspSnippetPart.Variable(name()))
      if (c != '{') return null
      i++
      if (i >= s.length) return null
      if (s[i].isDigit()) {
        val index = int()
        return when (s.getOrNull(i)) {
          '}' -> { i++; listOf(LspSnippetPart.TabStop(index)) }
          ':' -> {
            i++
            content(start) { LspSnippetPart.TabStop(index, it) }
          }
          '|' -> {
            i++
            val choices = choices() ?: return null
            listOf(LspSnippetPart.TabStop(index, listOf(LspSnippetPart.Text(choices.first())), choices))
          }
          '/' -> if (transform()) listOf(LspSnippetPart.TabStop(index)) else null
          else -> null
        }
      }
      if (!s[i].isNameStart()) return null
      val name = name()
      return when (s.getOrNull(i)) {
        '}' -> { i++; listOf(LspSnippetPart.Variable(name)) }
        ':' -> {
          i++
          content(start) { LspSnippetPart.Variable(name, it) }
        }
        '/' -> if (transform()) listOf(LspSnippetPart.Variable(name)) else null
        else -> null
      }
    }

    /** After `${1:` or `${NAME:` that begins at [start]: the content up to `}`. Unclosed content follows its opening text. */
    private fun content(start: Int, construct: (List<LspSnippetPart>) -> LspSnippetPart): List<LspSnippetPart>? {
      if (nesting >= MAX_NESTING) return null
      val opening = s.substring(start, i)
      nesting++
      val content = parseAny(untilBrace = true)
      nesting--
      if (s.getOrNull(i) != '}') return listOf(LspSnippetPart.Text(opening)) + content
      i++
      return listOf(construct(content))
    }

    /** After `${n|`: `a,b|}`; the escapes `\,` `\|` `\\` `\$` `\}` are literal. */
    private fun choices(): List<String>? {
      val result = ArrayList<String>()
      val current = StringBuilder()
      while (i < s.length) {
        val c = s[i]
        when {
          c == '\\' && i + 1 < s.length && s[i + 1] in ",|\\$}" -> { current.append(s[i + 1]); i += 2 }
          c == ',' -> { result += current.toString(); current.clear(); i++ }
          c == '|' -> {
            if (s.getOrNull(i + 1) != '}') return null
            i += 2
            result += current.toString()
            return result
          }
          else -> { current.append(c); i++ }
        }
      }
      return null
    }

    /**
     * At the first `/` of `/regex/format/options}`; true when it parsed up to and including the `}`. A format entry
     * (`${1:/upcase}`, `${1:+yes}`) may contain `/`, so it is skipped whole.
     */
    private fun transform(): Boolean {
      var slashes = 0
      while (i < s.length && slashes < 3) {
        when {
          s[i] == '\\' -> i += 2
          s[i] == '/' -> { slashes++; i++ }
          slashes == 2 && s[i] == '$' && s.getOrNull(i + 1) == '{' -> i = formatEntryEnd() ?: (i + 1)
          else -> i++
        }
      }
      if (slashes < 3) return false
      while (i < s.length && s[i].isLetter()) i++
      if (s.getOrNull(i) != '}') return false
      i++
      return true
    }

    /**
     * At `${` in a format: the index after the entry `${1}` or `${1:...}`, or null when it is not an entry (VS Code reads it as
     * text). The entry text has no `{`, so that the scan stops at the next construct.
     */
    private fun formatEntryEnd(): Int? {
      var j = i + 2
      val digits = j
      while (j < s.length && s[j].isDigit()) j++
      if (j == digits) return null
      if (s.getOrNull(j) == '}') return j + 1
      if (s.getOrNull(j) != ':') return null
      j++
      while (j < s.length) {
        when (s[j]) {
          '\\' -> j += 2
          '}' -> return j + 1
          '{' -> return null
          else -> j++
        }
      }
      return null
    }

    private fun int(): Int {
      val start = i
      while (i < s.length && s[i].isDigit()) i++
      return s.substring(start, i).toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun name(): String {
      val start = i
      while (i < s.length && (s[i].isNameStart() || s[i].isDigit())) i++
      return s.substring(start, i)
    }

    private fun Char.isNameStart(): Boolean = this == '_' || this in 'a'..'z' || this in 'A'..'Z'
  }
}

/**
 * A snippet ready for a live template: text, tab stop and end segments, and the tab stop variables in tab order.
 * Rules (VS Code semantics where live templates allow them):
 * - tab stops go in index order, `$0` is the end; without `$0` the end is after the text;
 * - a repeated index mirrors the first one (a live template mirrors a repeated variable);
 * - a placeholder is the default text; a nested tab stop inside it is flattened into that text (live template variables
 *   cannot nest);
 * - a choice is the first value with the others as a lookup;
 * - a known variable is its value; an empty or unknown one takes its default; an unknown one without a default becomes a
 *   placeholder with its name (the spec), after the numbered tab stops.
 */
internal data class LspSnippetTemplate(val segments: List<Segment>, val variables: List<Tab>) {
  sealed interface Segment {
    data class Text(val value: String) : Segment
    data class Stop(val name: String) : Segment
    data object End : Segment
  }

  data class Tab(val name: String, val default: String, val choices: List<String>?)

  /** The text when the snippet has no tab stops, with the end offset; null when it needs a live template. */
  fun plainText(): Pair<String, Int>? {
    if (variables.isNotEmpty()) return null
    val text = StringBuilder()
    var end = -1
    for (segment in segments) {
      when (segment) {
        is Segment.Text -> text.append(segment.value)
        is Segment.Stop -> Unit
        Segment.End -> end = text.length
      }
    }
    return text.toString() to (if (end < 0) text.length else end)
  }

  fun toTemplate(project: Project): Template {
    val template = TemplateManager.getInstance(project).createTemplate("", "")
    template.isToReformat = false
    template.setToIndent(false) // the server sends the snippet with the indentation it wants
    for (segment in segments) {
      when (segment) {
        is Segment.Text -> template.addTextSegment(segment.value)
        is Segment.Stop -> template.addVariableSegment(segment.name)
        Segment.End -> template.addEndVariable()
      }
    }
    for (tab in variables) {
      val node = if (tab.choices != null) ConstantNode(tab.default).withLookupStrings(tab.choices) else ConstantNode(tab.default)
      // not addVariable(name, expression, default, stop): on a built template it also appends a variable segment
      template.addVariable(Variable(tab.name, node, node, true, false))
    }
    return template
  }

  companion object {
    fun of(parts: List<LspSnippetPart>, resolve: (String) -> String?): LspSnippetTemplate = Builder(resolve).build(parts)
  }

  private class Builder(val resolve: (String) -> String?) {
    val segments = ArrayList<Segment>()
    val numbered = sortedMapOf<Int, Tab>()
    val unknown = LinkedHashMap<String, Tab>()
    var hasEnd = false

    fun build(parts: List<LspSnippetPart>): LspSnippetTemplate {
      add(parts)
      if (!hasEnd) segments += Segment.End
      return LspSnippetTemplate(merge(segments), numbered.values + unknown.values)
    }

    fun add(parts: List<LspSnippetPart>) {
      for (part in parts) {
        when (part) {
          is LspSnippetPart.Text -> segments += Segment.Text(part.value)
          is LspSnippetPart.TabStop -> if (part.index == 0) {
            segments += Segment.Text(flatten(part.placeholder))
            if (!hasEnd) segments += Segment.End
            hasEnd = true
          }
          else {
            val name = "TAB_${part.index}"
            val existing = numbered[part.index]
            if (existing == null || existing.default.isEmpty() && existing.choices == null) {
              numbered[part.index] = Tab(name, flatten(part.placeholder), part.choices)
            }
            segments += Segment.Stop(name)
          }
          is LspSnippetPart.Variable -> {
            val value = resolve(part.name)
            when {
              !value.isNullOrEmpty() -> segments += Segment.Text(value)
              part.default != null -> add(part.default)
              value == null -> {
                val name = "VAR_${part.name}"
                unknown.getOrPut(name) { Tab(name, part.name, null) }
                segments += Segment.Stop(name)
              }
            }
          }
        }
      }
    }

    fun flatten(parts: List<LspSnippetPart>): String = parts.joinToString("") { part ->
      when (part) {
        is LspSnippetPart.Text -> part.value
        is LspSnippetPart.TabStop -> flatten(part.placeholder)
        is LspSnippetPart.Variable -> {
          val value = resolve(part.name)
          when {
            !value.isNullOrEmpty() -> value
            part.default != null -> flatten(part.default)
            value == null -> part.name
            else -> ""
          }
        }
      }
    }

    fun merge(segments: List<Segment>): List<Segment> {
      val result = ArrayList<Segment>()
      val text = StringBuilder()
      for (segment in segments) {
        if (segment is Segment.Text) {
          text.append(segment.value)
          continue
        }
        if (text.isNotEmpty()) result += Segment.Text(text.toString()).also { text.clear() }
        result += segment
      }
      if (text.isNotEmpty()) result += Segment.Text(text.toString())
      return result
    }
  }
}

/**
 * The snippet variables of the spec: `TM_FILENAME`, `TM_FILENAME_BASE`, `TM_DIRECTORY`, `TM_FILEPATH` (native paths, as in
 * VS Code), `TM_LINE_INDEX`, `TM_LINE_NUMBER`, `TM_CURRENT_LINE`. `TM_SELECTED_TEXT` and `TM_CURRENT_WORD` are empty, because
 * the IDE removes that text before the snippet goes in. Any other name is unknown (null).
 */
internal object LspSnippetVariables {
  fun resolver(file: VirtualFile?, document: Document, offset: Int): (String) -> String? = { name ->
    val line = document.getLineNumber(offset.coerceIn(0, document.textLength))
    when (name) {
      "TM_FILENAME" -> file?.name.orEmpty()
      "TM_FILENAME_BASE" -> file?.nameWithoutExtension.orEmpty()
      "TM_DIRECTORY" -> file?.parent?.let(::nativePath).orEmpty()
      "TM_FILEPATH" -> file?.let(::nativePath).orEmpty()
      "TM_LINE_INDEX" -> line.toString()
      "TM_LINE_NUMBER" -> (line + 1).toString()
      "TM_CURRENT_LINE" -> document.immutableCharSequence.subSequence(document.getLineStartOffset(line), document.getLineEndOffset(line)).toString()
      "TM_SELECTED_TEXT", "TM_CURRENT_WORD" -> ""
      else -> null
    }
  }

  private fun nativePath(file: VirtualFile): String = file.fileSystem.getNioPath(file)?.toString() ?: file.path
}
