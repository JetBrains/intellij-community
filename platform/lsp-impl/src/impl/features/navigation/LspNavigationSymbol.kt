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
 * | [LspOpenBrowserNavigatableSymbol] | `textDocument/documentLink` | outside the IDE | yes | the link tooltip |
 * | [LspPathDocumentLinkSymbol] | `textDocument/documentLink` | a directory, or a file at its start | yes | none |
 * | [LspDefinitionSymbol] | `textDocument/definition`, `textDocument/typeDefinition` | a file at `targetSelectionRange` | no | none |
 *
 * What the table can't hold:
 * - A link's eager underline and its plain-hover tooltip are not features of these symbols at all.
 *   [LspHighlightingApplier][com.intellij.platform.lsp.impl.features.highlighting.LspHighlightingApplier] renders both from
 *   `LspDocumentLink`, before the reference is resolved and therefore before any symbol exists.
 * - Only a target that leaves the IDE is labelled on Ctrl+hover. For the other two, the label worth showing is what the
 *   server says the target is, which needs `textDocument/hover` and a hint that may arrive after the underline --
 *   IJPL-252179. A path is a poor stand-in: outside the server roots it is a full SDK path, and inside them it largely
 *   repeats the text under the pointer.
 * - [LspDefinitionSymbol] has no hover of its own either:
 *   [LspDocumentationTargetProvider][com.intellij.platform.lsp.impl.features.documentation.LspDocumentationTargetProvider]
 *   answers by offset, so any position in an LSP-backed file gets `textDocument/hover`, including one over a reference.
 * - The two underlines are different objects: a `HighlightInfo` from the applier, and a `RangeHighlighter` that
 *   `CtrlMouseHandler2` adds while a modifier is held. Over a document link, both can be on screen at once.
 * - A native `PsiReference` shadows all three, because `allReferencesAround` takes the first element with references walking
 *   from the leaf up to the file, and LSP's implicit references live on the `PsiFile`.
 * - An integration that turns LSP hover off gets no [LspDefinitionSymbol] on Ctrl+hover, only when an action is performed.
 *   Its own language support answers Ctrl+hover instead -- see [LspImplicitReferenceProvider].
 */
internal sealed interface LspNavigationSymbol : NavigatableSymbol, DocumentationTarget {

  /** [Symbol] and [DocumentationTarget] each declare this with their own return type. */
  override fun createPointer(): Pointer<out LspNavigationSymbol>
}
