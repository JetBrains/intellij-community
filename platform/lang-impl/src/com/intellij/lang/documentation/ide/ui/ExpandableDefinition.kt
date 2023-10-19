// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.lang.LangBundle
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.ContentUpdater
import kotlinx.coroutines.flow.flowOf

fun parseExpandableDefinition(doc: String, maxLines: Int): ExpandableDefinition? {
  val start = doc.indexOf(EXPANDABLE_DEFINITION_START)
  if (start < 0) return null
  val end = doc.indexOf(DEFINITION_END)
  if (end == -1) return null
  return ExpandableDefinition(doc.substring(0, start),
                              doc.substring(start + EXPANDABLE_DEFINITION_START.length, end),
                              doc.substring(end + DEFINITION_END.length), maxLines)
}

const val TOGGLE_EXPANDABLE_DEFINITION = "toggle.expandable.definition"

class ExpandableDefinition(private val prefix: String, definition: String, private val content: String, maxLines: Int): ContentUpdater {
  private val variants: Pair<String, String> = createVariants(definition, maxLines)
  private var expanded = false
  fun getDecorated(): String = prefix + DEFINITION_START + (if (expanded) variants.second else variants.first) + DEFINITION_END + content

  fun toggleExpanded() {
    expanded = !expanded
  }

  private fun createVariants(definition: String, maxLines: Int): Pair<String, String> {
    var offset = 0
    for (i in 0..<maxLines) {
      offset = definition.indexOf("<br>", offset)
      if (offset == -1) {
        return Pair(definition, definition)
      }
      offset += "<br>".length
    }
    val collapsed = definition.substring(0, offset) + HtmlChunk.link(TOGGLE_EXPANDABLE_DEFINITION, LangBundle.message("show.more"))
    val full = definition + HtmlChunk.br() + HtmlChunk.link(TOGGLE_EXPANDABLE_DEFINITION, LangBundle.message("show.less"))
    return Pair(collapsed, full)
  }

  override fun prepareContentUpdates(currentContent: String) = flowOf(getDecorated())
}