// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

/**
 * See LSP specification: [textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specification/#textDocument_onTypeFormatting).
 */
sealed class LspOnTypeFormattingCustomizer

open class LspOnTypeFormattingSupport : LspOnTypeFormattingCustomizer()

object LspOnTypeFormattingDisabled : LspOnTypeFormattingCustomizer()
