// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.io.URLUtil
import com.intellij.util.withScheme
import java.net.URI

object URIUtil {
  fun normalizeAndValidateHttpUri(uri: String): String {
    val normalized = URLUtil.addSchemaIfMissing(uri).removeSuffix("/")
    require(normalized.startsWith(URLUtil.HTTP_PROTOCOL)) { CollaborationToolsBundle.message("login.server.invalid") }
    URI.create(normalized)
    return normalized
  }

  fun isValidHttpUri(uri: String): Boolean {
    if (uri.isBlank()) return false
    return try {
      normalizeAndValidateHttpUri(uri)
      true
    }
    catch (_: Throwable) {
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

  fun createUriWithCustomScheme(uri: String, scheme: String): URI {
    val prefix = scheme + URLUtil.SCHEME_SEPARATOR
    if (uri.startsWith(prefix)) return URI(uri)
    return URI(prefix + removeProtocolPrefix(uri))
  }

  private fun removeProtocolPrefix(url: String): String {
    val index = url.indexOf(URLUtil.SCHEME_SEPARATOR)
    return if (index != -1) url.substring(index + URLUtil.SCHEME_SEPARATOR.length) else url
  }
}

fun URI.resolveRelative(path: String): URI {
  val newPath: String
  if (path.startsWith("/")) newPath = path.replace("//+", "/")
  else {
    val currentPath = this.toString() // to avoid path decoding
    if (currentPath.endsWith("/")) newPath = currentPath + path.replace("//+", "/")
    else newPath = currentPath + "/" + path.replace("//+", "/")
  }

  return resolve(newPath).normalize()
}

fun URI.withQuery(searchQuery: String): URI {
  if (searchQuery.isBlank()) return this
  val rawUri = this.toString() // to avoid path decoding
  return URI("$rawUri?$searchQuery")
}
