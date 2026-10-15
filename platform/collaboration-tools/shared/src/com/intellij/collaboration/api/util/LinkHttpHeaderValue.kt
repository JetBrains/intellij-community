// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.util

import org.apache.http.message.BasicHeaderValueParser

data class LinkHttpHeaderValue(
  val firstLink: String? = null,
  val prevLink: String? = null,
  val nextLink: String? = null,
  val lastLink: String? = null,
  val isDeprecated: Boolean = false,
) {
  companion object {
    const val HEADER_NAME: String = "Link"

    @JvmStatic
    fun parse(linkHeaderValue: String): LinkHttpHeaderValue {
      val headerElements = BasicHeaderValueParser.parseElements(linkHeaderValue, null)
        .mapNotNull {
          val relParam = checkNotNull(it.parameters.find { param -> param.name.equals("rel") }) {
            "Missing rel-param in: '$linkHeaderValue'"
          }

          val urlPart = (it.name ?: return@mapNotNull null) + (it.value?.let { "=$it" } ?: "")
          check(urlPart.startsWith("<") && urlPart.endsWith(">")) {
            "Invalid URL-part '$urlPart' in: '$linkHeaderValue'"
          }
          val url = urlPart.removeSurrounding("<", ">")

          relParam.value to url
        }
        .toMap()

      return LinkHttpHeaderValue(
        firstLink = headerElements["first"],
        prevLink = headerElements["prev"],
        nextLink = headerElements["next"],
        lastLink = headerElements["last"],
        isDeprecated = headerElements.containsKey("deprecation")
      )
    }
  }
}