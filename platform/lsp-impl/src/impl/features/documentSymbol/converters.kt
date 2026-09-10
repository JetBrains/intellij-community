package com.intellij.platform.lsp.impl.features.documentSymbol

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.jsonrpc.messages.Either

/**
 * Normalizes a `textDocument/documentSymbol` response to a [DocumentSymbol] tree.
 *
 * lsp4j merges the two response shapes into one list type.
 * VSCode declares `DocumentSymbol[] | SymbolInformation[]`,
 * but lsp4j declares `(DocumentSymbol | SymbolInformation)[]`.
 */
internal fun List<Either<SymbolInformation, DocumentSymbol>>.toDocumentSymbols(): List<DocumentSymbol> =
  if (firstOrNull()?.isLeft == true) toDocumentSymbolTree(mapNotNull { it.left })
  else mapNotNull { it.right }

@Suppress("DEPRECATION")
internal fun symbolInformationToDocumentSymbol(info: SymbolInformation): DocumentSymbol {
  val locationRange = info.location.range
  return DocumentSymbol().apply {
    name = info.name
    kind = info.kind
    range = locationRange
    selectionRange = locationRange
    detail = ""
    //containerName = info.containerName // VSCode removed that field
    tags = info.tags
    deprecated = info.deprecated // VSCode removed that field and migrated it into tags
    children = mutableListOf<DocumentSymbol>()
  }
}

// adapted from VSCode
@Suppress("DEPRECATION")
internal fun toDocumentSymbolTree(infos: List<SymbolInformation>): List<DocumentSymbol> {
  // Sort by start asc, then end desc
  val sorted = infos.sortedWith { a, b ->
    val res = comparePositions(a.location.range.start, b.location.range.start)
    if (res != 0) res
    else comparePositions(b.location.range.end, a.location.range.end)
  }

  val result = mutableListOf<DocumentSymbol>()
  val parentStack = ArrayDeque<DocumentSymbol>()

  for (info in sorted) {
    val element = symbolInformationToDocumentSymbol(info)

    while (true) {
      if (parentStack.isEmpty()) {
        parentStack.addLast(element)
        result.add(element)
        break
      }
      val parent = parentStack.last()
      if (containsRange(parent.range, element.range) && !equalsRange(parent.range, element.range)) {
        parent.children!!.add(element)
        parentStack.addLast(element)
        break
      }
      parentStack.removeLast()
    }
  }

  return result
}


internal fun comparePositions(a: Position, b: Position): Int {
  val lineDiff = a.line - b.line
  return if (lineDiff != 0) lineDiff else a.character - b.character
}

internal fun containsPosition(range: Range, position: Position): Boolean {
  return comparePositions(range.start, position) <= 0 &&
         comparePositions(position, range.end) <= 0
}

private fun containsRange(outer: Range, inner: Range): Boolean {
  return comparePositions(outer.start, inner.start) <= 0 &&
         comparePositions(outer.end, inner.end) >= 0
}

private fun equalsRange(a: Range, b: Range): Boolean {
  return comparePositions(a.start, b.start) == 0 &&
         comparePositions(a.end, b.end) == 0
}