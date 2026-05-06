// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import org.jetbrains.annotations.ApiStatus

/**
 * Customizes various features of the LSP support in the IDE.
 * Each property corresponds to an LSP feature and can be overridden with custom implementation.
 * To completely disable a feature, the property can be overridden to use the corresponding `Disabled` implementation.
 */
@ApiStatus.OverrideOnly
open class LspCustomization {
  /**
   * Customizes the LSP go to definition feature behavior. By default, uses standard [LspGoToDefinitionSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspGoToDefinitionSupport] that fine-tunes the behavior
   * - return [LspGoToDefinitionDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will ask the LSP server for `Navigate -> Declaration` target by sending the
   * [textDocument/definition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_definition) request.
   */
  open val goToDefinitionCustomizer: LspGoToDefinitionCustomizer = LspGoToDefinitionSupport()

  /**
   * Customizes the LSP go to type definition feature behavior. By default, uses standard [LspGoToTypeDefinitionSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspGoToTypeDefinitionSupport] that fine-tunes the behavior
   * - return [LspGoToTypeDefinitionDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will ask the LSP server for `Navigate -> Type Declaration` target by sending the
   * [textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specification/#textDocument_typeDefinition) request.
   */
  open val goToTypeDefinitionCustomizer: LspGoToTypeDefinitionCustomizer = LspGoToTypeDefinitionSupport()

  /**
   * Customizes the LSP hover feature behavior. By default, uses standard [LspHoverSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspHoverSupport] that fine-tunes the behavior
   * - return [LspHoverDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will ask the LSP server for hover information by sending the
   * [textDocument/hover](https://microsoft.github.io/language-server-protocol/specification/#textDocument_hover) request.
   */
  open val hoverCustomizer: LspHoverCustomizer = LspHoverSupport()

  /**
   * Customizes the LSP completion feature behavior. By default, uses standard [LspCompletionSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspCompletionSupport] that fine-tunes the behavior
   * - return [LspCompletionDisabled] to disable the LSP feature
   *
   * When enabled, handles completion requests:
   * - [textDocument/completion](https://microsoft.github.io/language-server-protocol/specification/#textDocument_completion)
   * - [completionItem/resolve](https://microsoft.github.io/language-server-protocol/specification/#completionItem_resolve)
   */
  open val completionCustomizer: LspCompletionCustomizer = LspCompletionSupport()

  /**
   * Customizes the LSP semantic tokens feature behavior. By default, uses standard [LspSemanticTokensSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspSemanticTokensSupport] that fine-tunes the behavior
   * - return [LspSemanticTokensDisabled] to disable the LSP feature
   *
   * See LSP specification:
   * [Semantic Tokens](https://microsoft.github.io/language-server-protocol/specification/#textDocument_semanticTokens)
   *
   * Plugins may provide basic syntax highlighting using not only information from the LSP server but also other approaches,
   * such as TextMate bundles.
   *
   * It's possible to control the feature per file, see [LspSemanticTokensSupport.shouldAskServerForSemanticTokens]
   */
  open val semanticTokensCustomizer: LspSemanticTokensCustomizer = LspSemanticTokensSupport()

  /**
   * Customizes the LSP diagnostics feature behavior. By default, uses standard [LspDiagnosticsSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspDiagnosticsSupport] that fine-tunes the behavior
   * - return [LspDiagnosticsDisabled] to disable the LSP feature
   *
   * When enabled, handles:
   * - Diagnostics pushed by the server ([textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specification/#textDocument_publishDiagnostics))
   * - Diagnostics pulled by the client ([textDocument/diagnostic](https://microsoft.github.io/language-server-protocol/specification/#textDocument_pullDiagnostics))
   */
  open val diagnosticsCustomizer: LspDiagnosticsCustomizer = LspDiagnosticsSupport()

  /**
   * Customizes the LSP code actions feature behavior. By default, uses standard [LspCodeActionsSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspCodeActionsSupport] that fine-tunes the behavior
   * - return [LspCodeActionsDisabled] to disable the LSP feature
   *
   * When enabled, handles [CodeAction](https://microsoft.github.io/language-server-protocol/specification#codeAction) objects
   * received from the LSP server.
   */
  open val codeActionsCustomizer: LspCodeActionsCustomizer = LspCodeActionsSupport()

  /**
   * Customizes the LSP commands feature behavior. By default, uses standard [LspCommandsSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspCommandsSupport] that fine-tunes the behavior
   * - return [LspCommandsDisabled] to disable the LSP feature
   *
   * When enabled, handles [Command](https://microsoft.github.io/language-server-protocol/specification#command) objects
   * received from the LSP server.
   *
   * @see LspCommandsSupport.executeCommand
   */
  open val commandsCustomizer: LspCommandsCustomizer = LspCommandsSupport()

  /**
   * Customizes the LSP formatting feature behavior. By default, uses standard [LspFormattingSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspFormattingSupport] that fine-tunes the behavior
   * - return [LspFormattingDisabled] to disable the LSP feature
   *
   * When enabled, it helps to decide whether the LSP server should be used for code formatting for each particular file.
   */
  open val formattingCustomizer: LspFormattingCustomizer = LspFormattingSupport()

  /**
   * Customizes the LSP on type formatting feature behavior. The feature is disabled by default.
   * Implementations may override this property to:
   * - return [LspOnTypeFormattingSupport] to enable the LSP feature
   *
   * When enabled, the IDE will send
   * [textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specification/#textDocument_onTypeFormatting)
   * request to the server when a trigger character is typed in the editor.
   */
  open val onTypeFormattingCustomizer: LspOnTypeFormattingCustomizer = LspOnTypeFormattingDisabled

  /**
   * Customizes the LSP find references feature behavior. By default, uses standard [LspFindReferencesSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspFindReferencesSupport] that fine-tunes the behavior
   * - return [LspFindReferencesDisabled] to disable the LSP feature
   *
   * When enabled, the `Find Usages` and `Show Usages` features will use the
   * [textDocument/references](https://microsoft.github.io/language-server-protocol/specification/#textDocument_references)
   * request to get results.
   */
  open val findReferencesCustomizer: LspFindReferencesCustomizer = LspFindReferencesSupport()

  /**
   * Customizes the "Optimize Imports" feature behavior. By default, uses standard [LspOptimizeImportsSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspOptimizeImportsSupport] that fine-tunes the behavior
   * - return [LspOptimizeImportsDisabled] to disable the feature
   *
   * When enabled, the IDE will use the
   * [textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeAction)
   * request with the `"source.organizeImports"` code action kind to optimize imports in the file.
   */
  open val optimizeImportsCustomizer: LspOptimizeImportsCustomizer = LspOptimizeImportsSupport()

  /**
   * Customizes the LSP document color feature behavior. By default, uses standard [LspDocumentColorSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspDocumentColorSupport] that fine-tunes the behavior
   * - return [LspDocumentColorDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentColor)
   * request to decorate color references in the editor.
   */
  open val documentColorCustomizer: LspDocumentColorCustomizer = LspDocumentColorSupport()

  /**
   * Customizes the LSP document link feature behavior. By default, uses standard [LspDocumentLinkSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspDocumentLinkSupport] that fine-tunes the behavior
   * - return [LspDocumentLinkDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentLink)
   * request to highlight links in the editor.
   */
  open val documentLinkCustomizer: LspDocumentLinkCustomizer = LspDocumentLinkSupport()

  /**
   * Customizes the LSP folding range feature behavior. By default, uses standard [LspFoldingRangeSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspFoldingRangeSupport] that fine-tunes the behavior
   * - return [LspFoldingRangeDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specification/#textDocument_foldingRange)
   * request to obtain code folding regions from the LSP server.
   */
  open val foldingRangeCustomizer: LspFoldingRangeCustomizer = LspFoldingRangeSupport()

  /**
   * Customizes the LSP inlay hints feature behavior.
   * Implementations may override this property to:
   * - return their specific subclass of [LspInlayHintSupport] that fine-tunes the behavior
   * - return [LspInlayHintDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specification/#textDocument_inlayHint)
   * request to display inline hints such as parameter names, type annotations, and other contextual information
   * directly in the editor.
   */
  open val inlayHintCustomizer: LspInlayHintCustomizer = LspInlayHintSupport()

  /**
   * Customizes the LSP highlight usages feature behavior. By default, uses standard [LspDocumentHighlightsSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspDocumentHighlightsSupport] that fine-tunes the behavior
   * - return [LspDocumentHighlightsDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentHighlight)
   * request to provide usage highlighting to be used in the editor.
   */
  open val documentHighlightsCustomizer: LspDocumentHighlightsCustomizer = LspDocumentHighlightsSupport()

  /**
   * Customizes the LSP signature help feature behavior. By default, uses standard [LspSignatureHelpSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspSignatureHelpSupport] that fine-tunes the behavior
   * - return [LspSignatureHelpDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specification/#textDocument_signatureHelp)
   * request to provide parameter hints when typing function/method calls.
   */
  open val signatureHelpCustomizer: LspSignatureHelpCustomizer = LspSignatureHelpSupport()

  /**
   * Customizes the LSP document symbol feature behavior. By default, uses standard [LspDocumentSymbolSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspDocumentSymbolCustomizer] that fine-tunes the behavior
   * - return [LspDocumentSymbolDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentSymbol)
   * request to get file structure and breadcrumbs from the LSP server.
   *
   * @see symbolKindCustomizer
   */
  open val documentSymbolCustomizer: LspDocumentSymbolCustomizer = LspDocumentSymbolSupport()

  /**
   * Customizes the LSP workspace symbol feature behavior. By default, uses standard [LspWorkspaceSymbolSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspWorkspaceSymbolSupport] that fine-tunes the behavior
   * - return [LspWorkspaceSymbolDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [workspace/symbol](https://microsoft.github.io/language-server-protocol/specification/#workspace_symbol)
   * request to provide workspace-wide symbol search functionality through the "Go To Symbol" feature.
   *
   * @see symbolKindCustomizer
   */
  open val workspaceSymbolCustomizer: LspWorkspaceSymbolCustomizer = LspWorkspaceSymbolSupport()


  /**
   * Customizes the LSP call hierarchy feature behavior. By default, uses standard [LspCallHierarchySupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspCallHierarchySupport] that fine-tunes the behavior
   * - return [LspCallHierarchyDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareCallHierarchy)
   * request to provide call hierarchy information for navigating between callers and callees of methods.
   */
  open val callHierarchyCustomizer: LspCallHierarchyCustomizer = LspCallHierarchySupport()

  /**
   * Customizes the LSP type hierarchy feature behavior. By default, uses standard [LspTypeHierarchySupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspTypeHierarchySupport] that fine-tunes the behavior
   * - return [LspTypeHierarchyDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareTypeHierarchy)
   * request to provide type hierarchy information for navigating between supertypes and subtypes.
   */
  open val typeHierarchyCustomizer: LspTypeHierarchyCustomizer = LspTypeHierarchySupport()

  /**
   * Shared customization for [documentSymbolCustomizer], [workspaceSymbolCustomizer], [callHierarchyCustomizer] and [typeHierarchyCustomizer].
   */
  open val symbolKindCustomizer: LspSymbolKindCustomizer = LspSymbolKindCustomizer()

  /**
   * Customizes the LSP selection range feature behavior. By default, uses standard [LspSelectionRangeSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspSelectionRangeSupport] that fine-tunes the behavior
   * - return [LspSelectionRangeDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specification/#textDocument_selectionRange)
   * request to provide "Extend Selection" / "Shrink Selection" features in the editor.
   */
  open val selectionRangeCustomizer: LspSelectionRangeCustomizer = LspSelectionRangeSupport()

  /**
   * Customizes the LSP code lens feature behavior. By default, uses standard [LspCodeLensSupport] implementation.
   * Implementations may override this property to:
   * - return their specific subclass of [LspCodeLensSupport] that fine-tunes the behavior
   * - return [LspCodeLensDisabled] to disable the LSP feature
   *
   * When enabled, the IDE will use the
   * [textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specification/#textDocument_codeLens)
   * request to provide code lenses (called "Code Vision" in the IDE) that display contextual information
   * and actions directly in the editor.
   */
  open val codeLensCustomizer: LspCodeLensCustomizer = LspCodeLensSupport()


  /**
   * Customizes the LSP Rename functionality.
   *
   * Allows overriding or extending the default behavior for renaming symbols.
   *
   * Defaults to [LspRenameSupport], which provides standard rename functionality.
   *
   * @see [textDocument/rename](https://microsoft.github.io/language-server-protocol/specification/#textDocument_rename)
   * @see [textDocument/prepareRename](https://microsoft.github.io/language-server-protocol/specification/#textDocument_prepareRename)
   */
  open val renameCustomizer: LspRenameCustomizer = LspRenameSupport()
}
