// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.lsp.api.LspPluginServerConfiguration
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler
import org.jetbrains.annotations.ApiStatus

/** Parses JSON initialization options from an LSP server configuration. */
@ApiStatus.Experimental
fun LspPluginServerConfiguration.createInitializationOptions(): Any? {
  if (initializationOptions.isBlank()) {
    return null
  }

  return try {
    MessageJsonHandler(emptyMap()).gson.fromJson(initializationOptions, Map::class.java)
  }
  catch (e: RuntimeException) {
    thisLogger().warn("Invalid JSON in initialization options for '$name': ${e.message}")
    null
  }
}
