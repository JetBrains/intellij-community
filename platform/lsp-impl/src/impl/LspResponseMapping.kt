// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.TextEdit

internal fun LspDocument.mapLocation(location: Location): Location =
  mapToHost(location) {
    Location(fileUri, toHostRange(it.range))
  }

internal fun LspDocument.mapLocationLink(locationLink: LocationLink): LocationLink =
  mapToHost(locationLink) {
    LocationLink(
      fileUri,
      toHostRange(it.targetRange),
      toHostRange(it.targetSelectionRange),
      // originSelectionRange is already in host coordinates (sent by the IDE side), no mapping needed
      it.originSelectionRange,
    )
  }

internal fun LspDocument.mapDocumentSymbol(documentSymbol: DocumentSymbol): DocumentSymbol =
  mapToHost(documentSymbol) {
    DocumentSymbol(
      it.name,
      it.kind,
      toHostRange(it.range),
      toHostRange(it.selectionRange),
      it.detail,
      it.children?.map { child -> mapDocumentSymbol(child) },
    )
  }

internal fun LspDocument.mapTextEdit(edit: TextEdit): TextEdit =
  mapToHost(edit) { TextEdit(toHostRange(it.range), it.newText) }

internal fun LspDocument.mapFoldingRange(foldingRange: FoldingRange): FoldingRange =
  mapToHost(foldingRange) {
    FoldingRange(toHostLine(it.startLine), toHostLine(it.endLine)).also { mapped ->
      mapped.kind = it.kind
      mapped.collapsedText = it.collapsedText
      mapped.startCharacter = it.startCharacter
      mapped.endCharacter = it.endCharacter
    }
  }

internal fun LspDocument.mapSelectionRange(selectionRange: SelectionRange): SelectionRange =
  mapToHost(selectionRange) {
    SelectionRange().apply {
      range = toHostRange(it.range)
      parent = it.parent?.let { p -> mapSelectionRange(p) }
    }
  }
