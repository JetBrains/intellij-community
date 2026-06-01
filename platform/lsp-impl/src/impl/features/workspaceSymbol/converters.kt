// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.workspaceSymbol

import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.SymbolTag
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either

@Suppress("DEPRECATION")
internal fun SymbolInformation.toWorkspaceSymbol(): WorkspaceSymbol {
  val ws = WorkspaceSymbol()
  ws.name = name
  ws.kind = kind
  ws.containerName = containerName
  ws.location = Either.forLeft(location)

  var tags = tags
  if (deprecated == true && (tags == null || !tags.contains(SymbolTag.Deprecated))) {
    tags = tags ?: mutableListOf()
    tags.add(SymbolTag.Deprecated)
  }
  ws.tags = tags

  return ws
}
