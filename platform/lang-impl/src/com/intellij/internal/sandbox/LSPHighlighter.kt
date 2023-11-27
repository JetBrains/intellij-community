// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.sandbox

import com.intellij.openapi.util.Segment
import java.util.stream.Stream
// client: connect to LSP server, read back diagnostics, convert them to highlights
class LSPHighlighter(val lspServer: LSPServer) : Highlighter {
  override fun highlightingPass(session: HighlightSession) {
    lspServer.notifyOfChanges();

    lspServer.diagnostics().forEach { diagnostic ->
      session.sink.newHighlight {
        range(diagnostic.range)
        description(diagnostic.description)
      }
    }
  }
}

interface LSPServer {
  fun notifyOfChanges()
  fun diagnostics(): Stream<Diagnostic>
  interface Diagnostic {
    val range: Segment
    val description: String
  }
}