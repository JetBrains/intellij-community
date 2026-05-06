// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.util

import com.intellij.platform.lsp.util.Lsp4jService
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

internal class Lsp4jServiceImpl : Lsp4jService() {
  override fun extractLocationsFromJson(arguments: List<Any>?): List<Location>? {
    if (arguments.isNullOrEmpty()) return null

    val jsonHandler = MessageJsonHandler(emptyMap())
    for (arg in arguments) {
      val jsonElement = jsonHandler.gson.toJsonTree(arg)
      if (jsonElement.isJsonArray && looksLikeLocationArray(jsonElement.asJsonArray)) {
        return jsonHandler.gson.fromJson(jsonElement, Array<Location>::class.java).toList()
      }
    }
    return null
  }

  private fun looksLikeLocationArray(array: com.google.gson.JsonArray): Boolean {
    val first = array.firstOrNull() ?: return false
    if (!first.isJsonObject) return false
    val obj = first.asJsonObject
    return obj.has("uri") && obj.has("range")
  }
}
