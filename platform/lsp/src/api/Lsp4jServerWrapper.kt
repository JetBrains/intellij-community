// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
@TestOnly
fun interface Lsp4jServerWrapper {
  fun wrapLsp4jServer(lspServer: LspServer, lsp4jServer: Lsp4jServer): Lsp4jServer
}