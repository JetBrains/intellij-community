// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation.ide.ui

import com.intellij.lang.LangBundle
import com.intellij.lang.documentation.DocumentationMarkup.DEFINITION_END
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.platform.backend.documentation.ContentUpdater
import com.intellij.platform.backend.documentation.DocumentationContentData
import kotlinx.coroutines.flow.flowOf

internal fun createExpandableDefinition(contentData: DocumentationContentData): ExpandableDefinition? {
  if (contentData.definitionDetails == null) return null
  val doc = contentData.html
  val end = doc.indexOf(DEFINITION_END)
  if (end == -1) return null
  return ExpandableDefinition(doc.substring(0, end), doc.substring(end), contentData.definitionDetails)
}

const val TOGGLE_EXPANDABLE_DEFINITION = "toggle.expandable.definition"

class ExpandableDefinition(private val definition: String, private val content: String, details: String): ContentUpdater {
  private val variants: Pair<String, String> = createVariants(definition, details)
  private var expanded = false
  fun getDecorated(): String = (if (expanded) variants.second else variants.first) + content

  fun toggleExpanded() {
    expanded = !expanded
  }

  private fun createVariants(definition: String, details: String): Pair<String, String> {
    val collapsed = definition + HtmlChunk.link(TOGGLE_EXPANDABLE_DEFINITION, LangBundle.message("show.more"))
    val full = definition + details + HtmlChunk.br() + HtmlChunk.link(TOGGLE_EXPANDABLE_DEFINITION, LangBundle.message("show.less"))
    return Pair(collapsed, full)
  }

  override fun prepareContentUpdates(currentContent: String) = flowOf(getDecorated())
}