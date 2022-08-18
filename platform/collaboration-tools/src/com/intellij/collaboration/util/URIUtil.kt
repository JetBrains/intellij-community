// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.util

import com.intellij.collaboration.messages.CollaborationToolsBundle
import java.net.URI

object URIUtil {

  fun normalizeAndValidateHttpUri(uri: String): String {
    val normalized = addHttpsSchemaIfMissing(uri)
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
}