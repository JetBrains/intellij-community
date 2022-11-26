// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.URLUtil
import com.intellij.util.withScheme
import java.net.URI

object URIUtil {

  fun normalizeAndValidateHttpUri(uri: String): String {
    val normalized = addHttpsSchemaIfMissing(uri).removeSuffix("/")
    require(uri.startsWith("http")) { CollaborationToolsBundle.message("login.server.invalid") }
    URI.create(normalized)
    return normalized
  }

  private fun addHttpsSchemaIfMissing(uri: String): String {
    if (uri.contains("://")) return uri
    return "https://$uri"
  }

  fun isValidHttpUri(uri: String): Boolean {
    return try {
      normalizeAndValidateHttpUri(uri)
      true
    }
    catch (e: Throwable) {
      false
    }
  }

  fun equalWithoutSchema(first: URI, second: URI): Boolean {
    val stubScheme = "stub"
    return first.withScheme(stubScheme) == second.withScheme(stubScheme)
  }

  fun toStringWithoutScheme(uri: URI): @NlsSafe String {
    val schemeText = uri.scheme + URLUtil.SCHEME_SEPARATOR
    return uri.toString().removePrefix(schemeText)
  }
}

fun URI.resolveRelative(path: String): URI = resolve("./$path")

fun URI.withQuery(searchQuery: String): URI {
  if (searchQuery.isBlank()) return this
  val rawUri = this.toString() // to avoid path decoding
  return URI("$rawUri?$searchQuery")
}