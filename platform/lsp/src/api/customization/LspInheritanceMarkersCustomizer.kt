// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.openapi.vfs.VirtualFile
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.SymbolKind

/**
 * Customizes the override/implement gutter icons (line markers) for LSP-backed files.
 *
 * The feature combines three standard LSP requests:
 * - [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentSymbol)
 *   provides the marker anchors.
 * - [textDocument/implementation](https://microsoft.github.io/language-server-protocol/specification/#textDocument_implementation)
 *   provides the "implemented/overridden by" targets for a method-like symbol.
 * - [prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareTypeHierarchy)
 *   with `typeHierarchy/subtypes` provides the "subtypes" targets for a class-like symbol.
 *
 * Traffic gates keep the per-symbol request count low.
 * The IDE queries an interface member always.
 * The IDE queries a class method only when `typeHierarchy/subtypes` confirmed that the class has subtypes.
 * The IDE does not query a top-level function or a method of another container kind.
 */
sealed class LspInheritanceMarkersCustomizer

open class LspInheritanceMarkersSupport : LspInheritanceMarkersCustomizer() {
  /**
   * Returns true if the IDE should ask the server for inheritance markers for the given file.
   *
   * One pull costs one `documentSymbol` request plus one request per queried symbol.
   * Override this method to limit the feature to files where the server answers fast.
   */
  open fun shouldAskServerForMarkers(file: VirtualFile): Boolean = true

  /**
   * A method-like symbol gets an "implemented/overridden by" marker from the `textDocument/implementation` request.
   */
  open fun isMethodLikeSymbol(kind: SymbolKind): Boolean =
    kind == SymbolKind.Method || kind == SymbolKind.Function

  /**
   * A class-like symbol gets a "subtypes" marker from the `typeHierarchy/subtypes` request.
   */
  open fun isClassLikeSymbol(kind: SymbolKind): Boolean =
    kind == SymbolKind.Class || kind == SymbolKind.Interface || kind == SymbolKind.Struct
}

object LspInheritanceMarkersDisabled : LspInheritanceMarkersCustomizer()

/**
 * One computed gutter marker: the relation kind, the symbol it decorates, and the navigation targets.
 */
class LspInheritanceMarker(
  val kind: LspInheritanceMarkerKind,
  val symbolName: String,
  val symbolKind: SymbolKind,
  val targets: List<Location>,
)

enum class LspInheritanceMarkerKind {
  /** An interface member that has implementations. Computed with `textDocument/implementation`. */
  IMPLEMENTED_METHOD,

  /** A class method that is overridden in a subclass. Computed with `textDocument/implementation`. */
  OVERRIDDEN_METHOD,

  /** An interface that has implementations. Computed with `typeHierarchy/subtypes`. */
  IMPLEMENTED_CLASS,

  /** A class that has subtypes. Computed with `typeHierarchy/subtypes`. */
  SUBCLASSED_CLASS,
}
