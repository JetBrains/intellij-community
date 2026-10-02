// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.util.getRangeInDocument
import org.eclipse.lsp4j.LocationLink

/**
 * Response to a `textDocument/definition` or `textDocument/typeDefinition` request,
 * bundled with the range in the requested document that the links originate from.
 */
internal data class TextRangeAndLocationLinks(val textRange: TextRange, val locationLinks: List<LocationLink>) {
  internal companion object {
    /**
     * [textRange] is the union of the origin ranges reported by the server,
     * or an empty range at [offset] when the server reported none.
     */
    internal fun fromLocationLinks(
      locationLinks: List<LocationLink>,
      document: Document?,
      offset: Int,
    ): TextRangeAndLocationLinks {
      val originRange = document?.let {
        locationLinks
          .mapNotNull { link -> link.originSelectionRange }
          .mapNotNull { range -> getRangeInDocument(document, range) }
          .reduceOrNull(TextRange::union)
      }
      return TextRangeAndLocationLinks(originRange ?: TextRange(offset, offset), locationLinks)
    }
  }
}
