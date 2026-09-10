// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.lineMarkers

import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspDocument
import com.intellij.platform.lsp.impl.features.documentSymbol.containsPosition
import com.intellij.platform.lsp.impl.mapLocation
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams

/**
 * Sends `textDocument/implementation` and returns the raw targets, or `null` when the request failed.
 */
internal suspend fun LspClientImpl.requestImplementationTargets(lspDocument: LspDocument, position: Position): List<Location>? {
  val params = ImplementationParams(lspDocument.id, position)
  val response = sendRequest { it.textDocumentService.implementation(params) } ?: return null
  return response.map(
    { locations -> locations.map { Location(it.uri, it.range) } },
    { links -> links.map { Location(it.targetUri, it.targetSelectionRange ?: it.targetRange) } },
  )
}

/**
 * Sends `textDocument/prepareTypeHierarchy` and `typeHierarchy/subtypes`, and returns the raw targets,
 * or `null` when a request failed or the position has no hierarchy item.
 */
internal suspend fun LspClientImpl.requestSubtypeTargets(lspDocument: LspDocument, position: Position): List<Location>? {
  val prepareParams = TypeHierarchyPrepareParams(lspDocument.id, position)
  val items = sendRequest { it.textDocumentService.prepareTypeHierarchy(prepareParams) } ?: return null
  val item = items.firstOrNull() ?: return null
  val subtypes = sendRequest { it.textDocumentService.typeHierarchySubtypes(TypeHierarchySubtypesParams(item)) } ?: return null
  return subtypes.map { Location(it.uri, it.selectionRange ?: it.range) }
}

/**
 * Maps the targets to the host file coordinates, and drops the queried symbol itself.
 * Some servers include the queried symbol in the `textDocument/implementation` results.
 * A self reference is a same-file target whose range covers the anchor position.
 * The range check also catches a server that reports the full declaration range.
 */
internal fun LspClientImpl.mapTargetsToHost(
  sourceDocument: LspDocument,
  hostAnchorStart: Position,
  targets: List<Location>,
): List<Location> =
  targets
    .map { documentMapping.findDocumentByUrl(it.uri)?.mapLocation(it) ?: it }
    .filterNot { it.uri == sourceDocument.fileUri && containsPosition(it.range, hostAnchorStart) }
    .distinct()
