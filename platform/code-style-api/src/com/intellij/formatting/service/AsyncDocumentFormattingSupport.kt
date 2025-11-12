// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.formatting.FormattingContext
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface AsyncDocumentFormattingSupportFactory {
  fun create(service: AsyncDocumentFormattingService): AsyncDocumentFormattingSupport
}

@ApiStatus.Internal
interface AsyncDocumentFormattingSupport {
  fun formatDocument(
    document: Document,
    formattingRanges: List<TextRange>,
    formattingContext: FormattingContext,
    canChangeWhiteSpaceOnly: Boolean,
    quickFormat: Boolean,
  )
}