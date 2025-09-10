// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.inspectopedia.extractor.data

import com.intellij.openapi.util.NlsSafe
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.safety.Safelist

private val WHITELIST = Safelist()
  .addTags("p", "br", "li", "ul", "ol", "b", "i", "code", "a")
  .addAttributes("a", "href")

internal data class Inspection(
  @JvmField var id: String? = null,

  @JvmField val name: String,
  @JvmField val severity: String = "WARNING",
  @JvmField val path: List<String> = mutableListOf(),
  @JvmField val language: String? = null,
  @JvmField val isAppliesToDialects: Boolean = true,
  @JvmField val isCleanup: Boolean = false,
  @JvmField val isEnabledDefault: Boolean = true,
  @JvmField val briefDescription: String? = null,
  @JvmField val extendedDescription: String? = null,
  @JvmField val isHasOptionsPanel: Boolean = false,
  @JvmField val options: List<OptionsPanelInfo>? = null,
  @JvmField val cweIds: List<Int>? = null,
  @JvmField val codeQualityCategory: String? = null,
) : Comparable<Inspection> {
  fun cleanHtml(@NlsSafe src: String): String {
    val doc = Jsoup.parse(Jsoup.clean(src, WHITELIST))
    doc.select("ul").forEach { it.tagName("list") }
    doc.select("ol").forEach {
      it.tagName("list")
      it.attr("type", "decimal")
    }

    doc.select("code").forEach { it.text(it.text()) }
    doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
    return doc.body().html()
  }

  override fun compareTo(other: Inspection) = name.compareTo(other.name)
}

internal data class OptionsPanelInfo(
  @JvmField var type: String? = null,
  @JvmField var text: String? = null,
  @JvmField var id: String? = null,
) {
  @JvmField
  var description: String? = null
  @JvmField
  var value: Any? = null
  @JvmField
  var content: List<String>? = null // drop-down content

  @JvmField
  var children: List<OptionsPanelInfo>? = null
}
