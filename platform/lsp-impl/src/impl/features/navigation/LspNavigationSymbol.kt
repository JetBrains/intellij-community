// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.model.Pointer
import com.intellij.model.Symbol
import com.intellij.navigation.NavigatableSymbol
import com.intellij.platform.backend.documentation.DocumentationTarget

/**
 * A place an LSP server can send the IDE to. There are three kinds, and they differ in more than where they lead:
 *
 * | | reported by | navigates to | underlined without a modifier | Ctrl+hover label |
 * |---|---|---|---|---|
 * | [LspOpenBrowserNavigatableSymbol] | `textDocument/documentLink` | outside the IDE | yes | the URL |
 * | [LspPathDocumentLinkSymbol] | `textDocument/documentLink` | a directory, or a file at its start | yes | the target path |
 * | [LspDefinitionSymbol] | `textDocument/definition`, `textDocument/typeDefinition` | a file at `targetSelectionRange` | no | the target path |
 *
 * What the table can't hold:
 * - A link's eager underline and its plain-hover tooltip are not features of these symbols at all.
 *   [LspHighlightingApplier][com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier] renders both from
 *   `LspDocumentLink`, before the reference is resolved and therefore before any symbol exists.
 * - [LspDefinitionSymbol] has no hover of its own either:
 *   [LspDocumentationTargetProvider][com.intellij.platform.lsp.impl.features.documentation.LspDocumentationTargetProvider]
 *   answers by offset, so any position in an LSP-backed file gets `textDocument/hover`, including one over a reference.
 * - The two underlines are different objects: a `HighlightInfo` from the applier, and a `RangeHighlighter` that
 *   `CtrlMouseHandler2` adds while a modifier is held. Over a document link, both can be on screen at once.
 * - A native `PsiReference` shadows all three, because `allReferencesAround` takes the first element with references walking
 *   from the leaf up to the file, and LSP's implicit references live on the `PsiFile`.
 */
internal sealed interface LspNavigationSymbol : NavigatableSymbol, DocumentationTarget {

  /** [Symbol] and [DocumentationTarget] each declare this with their own return type. */
  override fun createPointer(): Pointer<out LspNavigationSymbol>
}
