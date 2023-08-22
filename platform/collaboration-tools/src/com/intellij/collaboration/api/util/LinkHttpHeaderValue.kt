// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.util

class LinkHttpHeaderValue constructor(val firstLink: String? = null,
                                      val prevLink: String? = null,
                                      val nextLink: String? = null,
                                      val lastLink: String? = null) {
  companion object {
    const val HEADER_NAME = "Link"

    private val HEADER_SECTION_REGEX = Regex("""^<(.*)>; rel="(first|prev|next|last)"$""")

    @JvmStatic
    fun parse(linkHeaderValue: String): LinkHttpHeaderValue {
      var firstLink: String? = null
      var prevLink: String? = null
      var nextLink: String? = null
      var lastLink: String? = null

      val split = linkHeaderValue.split(", ")
      check(split.isNotEmpty()) { "Cannot parse link header: $linkHeaderValue" }
      for (section in split) {
        if (section.isBlank()) continue
        val matchResult = HEADER_SECTION_REGEX.matchEntire(section) ?: error("Incorrect header section format: $section")
        val groupValues = matchResult.groupValues
        if (groupValues.size == 3) {
          when (groupValues[2]) {
            "first" -> firstLink = groupValues[1]
            "prev" -> prevLink = groupValues[1]
            "next" -> nextLink = groupValues[1]
            "last" -> lastLink = groupValues[1]
          }
        }
      }

      return LinkHttpHeaderValue(firstLink, prevLink, nextLink, lastLink)
    }
  }
}