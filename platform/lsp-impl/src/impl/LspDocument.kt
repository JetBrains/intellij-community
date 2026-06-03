// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl

import com.intellij.openapi.editor.Document
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.jetbrains.annotations.ApiStatus

/**
 * Models a single LSP `TextDocument` from the IDE side of the protocol.
 *
 * For a regular file this is the file itself. For a notebook file there is one [LspDocument] per cell.
 *
 * Coordinates carried by [id] are in this document's own space (cell-local for a notebook cell).
 * The [toHostPosition]/[toHostRange]/[toHostLine] helpers translate them back to the host file's
 * coordinate space so the rest of the platform sees positions in the file the user edits.
 */
@ApiStatus.Internal
interface LspDocument {
  /**
   * The URI of the host [VirtualFile][com.intellij.openapi.vfs.VirtualFile] (without cell fragment).
   * For regular files, this is the file URI. For notebook cells, this is the notebook file URI.
   * Use [id] for the LSP `TextDocument` identifier (which may include a cell fragment).
   */
  val fileUri: String

  /**
   * Identifier sent to the server in `textDocument/...` requests.
   */
  val id: TextDocumentIdentifier

  fun toHostPosition(position: Position): Position
  fun toHostRange(range: Range): Range
  fun toHostLine(line: Int): Int

  /**
   * Translates [input] from this document's coordinate space to host-file space.
   *
   * For identity documents (regular files) returns [input] as-is without invoking [transform].
   * For documents that need translation (notebook cells) invokes [transform] with this document
   * as the receiver so it can call [toHostRange], [toHostPosition], [toHostLine], or nested mappers.
   */
  fun <T> mapToHost(input: T, transform: LspDocument.(T) -> T): T

  /**
   * Returns a [Document] whose coordinate space matches this LSP document, suitable for
   * translating LSP `(line, character)` positions to offsets and back.
   *
   * Identity documents (regular files) may skip invoking [build] and return [hostDocument] directly.
   */
  fun prepareDocument(hostDocument: Document, build: (Document) -> Document?): Document? {
    return build(hostDocument)
  }
}

/**
 * Position in an LSP document with document identifier.
 *
 * For notebooks, the document may contain a cell URI (e.g., "file.ipynb#cell-0")
 * and the position is cell-relative.
 */
@ApiStatus.Internal
data class LspDocumentPosition(
  val document: LspDocument,
  val position: Position,
)

/**
 * Range in an LSP document with document identifier.
 *
 * For notebooks, the document may contain a cell URI and the range is cell-relative.
 */
@ApiStatus.Internal
data class LspDocumentRange(
  val document: LspDocument,
  val range: Range,
)
