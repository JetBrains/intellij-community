@file:Suppress("unused")

package com.jetbrains.lsp.implementation

import com.jetbrains.lsp.protocol.CallHierarchyIncomingCall
import com.jetbrains.lsp.protocol.CallHierarchyIncomingCallsParams
import com.jetbrains.lsp.protocol.CallHierarchyItem
import com.jetbrains.lsp.protocol.CallHierarchyOutgoingCall
import com.jetbrains.lsp.protocol.CallHierarchyOutgoingCallsParams
import com.jetbrains.lsp.protocol.CallHierarchyPrepareParams
import com.jetbrains.lsp.protocol.CallHierarchyRequests
import com.jetbrains.lsp.protocol.CodeAction
import com.jetbrains.lsp.protocol.CodeActionKind
import com.jetbrains.lsp.protocol.CodeActionParams
import com.jetbrains.lsp.protocol.CodeActionRegistrationOptions
import com.jetbrains.lsp.protocol.CodeActions
import com.jetbrains.lsp.protocol.CodeLens
import com.jetbrains.lsp.protocol.CodeLensParams
import com.jetbrains.lsp.protocol.CodeLensRegistrationOptions
import com.jetbrains.lsp.protocol.CodeLenses
import com.jetbrains.lsp.protocol.CommandOrCodeAction
import com.jetbrains.lsp.protocol.Commands
import com.jetbrains.lsp.protocol.CompletionItem
import com.jetbrains.lsp.protocol.CompletionOptions
import com.jetbrains.lsp.protocol.CompletionParams
import com.jetbrains.lsp.protocol.CompletionRegistrationOptions
import com.jetbrains.lsp.protocol.CompletionRequestType
import com.jetbrains.lsp.protocol.CompletionResolveRequestType
import com.jetbrains.lsp.protocol.CompletionResult
import com.jetbrains.lsp.protocol.DeclarationRegistrationOptions
import com.jetbrains.lsp.protocol.DefinitionParams
import com.jetbrains.lsp.protocol.DefinitionRegistrationOptions
import com.jetbrains.lsp.protocol.DefinitionRequestType
import com.jetbrains.lsp.protocol.DiagnosticOptions
import com.jetbrains.lsp.protocol.Diagnostics
import com.jetbrains.lsp.protocol.DocumentColorRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentDiagnosticParams
import com.jetbrains.lsp.protocol.DocumentDiagnosticReport
import com.jetbrains.lsp.protocol.DocumentFormattingParams
import com.jetbrains.lsp.protocol.DocumentFormattingRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentHighlightOptions
import com.jetbrains.lsp.protocol.DocumentLinkOptions
import com.jetbrains.lsp.protocol.DocumentOnTypeFormattingOptions
import com.jetbrains.lsp.protocol.DocumentRangeFormattingParams
import com.jetbrains.lsp.protocol.DocumentRangeFormattingRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentSelector
import com.jetbrains.lsp.protocol.DocumentSymbol
import com.jetbrains.lsp.protocol.DocumentSymbolParams
import com.jetbrains.lsp.protocol.DocumentSymbolRegistrationOptions
import com.jetbrains.lsp.protocol.DocumentSymbolRequest
import com.jetbrains.lsp.protocol.ExecuteCommandOptions
import com.jetbrains.lsp.protocol.ExecuteCommandParams
import com.jetbrains.lsp.protocol.FileOperationFilter
import com.jetbrains.lsp.protocol.FileOperationRegistrationOptions
import com.jetbrains.lsp.protocol.FileOperations
import com.jetbrains.lsp.protocol.FoldingRange
import com.jetbrains.lsp.protocol.FoldingRangeParams
import com.jetbrains.lsp.protocol.FoldingRangeRequestType
import com.jetbrains.lsp.protocol.FormattingRequestType
import com.jetbrains.lsp.protocol.Hover
import com.jetbrains.lsp.protocol.HoverParams
import com.jetbrains.lsp.protocol.HoverRegistrationOptions
import com.jetbrains.lsp.protocol.HoverRequestType
import com.jetbrains.lsp.protocol.Implementation
import com.jetbrains.lsp.protocol.ImplementationParams
import com.jetbrains.lsp.protocol.ImplementationRegistrationOptions
import com.jetbrains.lsp.protocol.InlayHint
import com.jetbrains.lsp.protocol.InlayHintParams
import com.jetbrains.lsp.protocol.InlayHintRegistrationOptions
import com.jetbrains.lsp.protocol.InlayHints
import com.jetbrains.lsp.protocol.InlineValueOptions
import com.jetbrains.lsp.protocol.LinkedEditingRangeOptions
import com.jetbrains.lsp.protocol.Location
import com.jetbrains.lsp.protocol.Locations
import com.jetbrains.lsp.protocol.MonikerOptions
import com.jetbrains.lsp.protocol.NotebookDocumentSyncOptions
import com.jetbrains.lsp.protocol.OrBoolean
import com.jetbrains.lsp.protocol.PrepareRenameParams
import com.jetbrains.lsp.protocol.PrepareRenameRequestType
import com.jetbrains.lsp.protocol.PrepareRenameResult
import com.jetbrains.lsp.protocol.RangeFormattingRequestType
import com.jetbrains.lsp.protocol.ReferenceParams
import com.jetbrains.lsp.protocol.ReferenceRegistrationOptions
import com.jetbrains.lsp.protocol.ReferenceRequestType
import com.jetbrains.lsp.protocol.RenameFilesParams
import com.jetbrains.lsp.protocol.RenameParams
import com.jetbrains.lsp.protocol.RenameRegistrationOptions
import com.jetbrains.lsp.protocol.RenameRequestType
import com.jetbrains.lsp.protocol.SelectionRangeOptions
import com.jetbrains.lsp.protocol.SemanticTokens
import com.jetbrains.lsp.protocol.SemanticTokensLegend
import com.jetbrains.lsp.protocol.SemanticTokensParams
import com.jetbrains.lsp.protocol.SemanticTokensRangeParams
import com.jetbrains.lsp.protocol.SemanticTokensRegistrationOptions
import com.jetbrains.lsp.protocol.SemanticTokensRequests
import com.jetbrains.lsp.protocol.ServerWorkspaceCapabilities
import com.jetbrains.lsp.protocol.SignatureHelp
import com.jetbrains.lsp.protocol.SignatureHelpParams
import com.jetbrains.lsp.protocol.SignatureHelpRegistrationOptions
import com.jetbrains.lsp.protocol.SignatureHelpRequest
import com.jetbrains.lsp.protocol.TextDocumentSync
import com.jetbrains.lsp.protocol.TextEdit
import com.jetbrains.lsp.protocol.TypeDefinitionParams
import com.jetbrains.lsp.protocol.TypeDefinitionRegistrationOptions
import com.jetbrains.lsp.protocol.TypeDefinitionRequestType
import com.jetbrains.lsp.protocol.TypeHierarchyItem
import com.jetbrains.lsp.protocol.TypeHierarchyPrepareParams
import com.jetbrains.lsp.protocol.TypeHierarchyRequests
import com.jetbrains.lsp.protocol.TypeHierarchySubtypesParams
import com.jetbrains.lsp.protocol.TypeHierarchySupertypesParams
import com.jetbrains.lsp.protocol.Workspace
import com.jetbrains.lsp.protocol.WorkspaceEdit
import com.jetbrains.lsp.protocol.WorkspaceSymbol
import com.jetbrains.lsp.protocol.WorkspaceSymbolParams
import com.jetbrains.lsp.protocol.WorkspaceSymbolRegistrationOptions
import com.jetbrains.lsp.protocol.WorkspaceSymbolRequests
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement

/**
 * File contains DSL functions for every ServerCapabilities's field
 */

fun LspServerCapabilitiesBuilder.textDocumentSync(
    value: TextDocumentSync,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(textDocumentSync = value) }, register = register)

fun LspServerCapabilitiesBuilder.notebookDocumentSync(
    value: NotebookDocumentSyncOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(notebookDocumentSync = value) }, register = register)

fun LspServerCapabilitiesBuilder.completionProvider(
    documentSelector: DocumentSelector? = null,
    triggerCharacters: List<String>? = null,
    allCommitCharacters: List<String>? = null,
    completionItem: CompletionOptions.CompletionItemCapabilities? = null,
    workDoneProgress: Boolean? = null,
    resolveHandler: (suspend context(LspHandlerContext) CoroutineScope.(CompletionItem) -> CompletionItem)? = null,
    handler: suspend context(LspHandlerContext) CoroutineScope.(CompletionParams) -> CompletionResult?,
): Unit = capability(update = {
    copy(
        completionProvider = CompletionRegistrationOptions(
            documentSelector = documentSelector,
            triggerCharacters = triggerCharacters,
            allCommitCharacters = allCommitCharacters,
            resolveProvider = resolveHandler != null,
            completionItem = completionItem,
            workDoneProgress = workDoneProgress,
        )
    )
}, register = {
    request(CompletionRequestType, handler)
    if (resolveHandler != null) {
        request(CompletionResolveRequestType, resolveHandler)
    }
})

fun LspServerCapabilitiesBuilder.hoverProvider(
    value: OrBoolean<HoverRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(HoverParams) -> Hover?,
): Unit = capability(update = { copy(hoverProvider = value) }, register = {
    request(HoverRequestType, handler)
})

fun LspServerCapabilitiesBuilder.signatureHelpProvider(
    value: SignatureHelpRegistrationOptions,
    handler: suspend context(LspHandlerContext) CoroutineScope.(SignatureHelpParams) -> SignatureHelp?,
): Unit = capability(update = { copy(signatureHelpProvider = value) }, register = {
    request(SignatureHelpRequest, handler)
})

fun LspServerCapabilitiesBuilder.declarationProvider(
    value: OrBoolean<DeclarationRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(declarationProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.definitionProvider(
    value: OrBoolean<DefinitionRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(DefinitionParams) -> List<Location>,
): Unit = capability(update = { copy(definitionProvider = value) }, register = {
    request(DefinitionRequestType, handler)
})

fun LspServerCapabilitiesBuilder.typeDefinitionProvider(
    value: OrBoolean<TypeDefinitionRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(TypeDefinitionParams) -> Locations?,
): Unit = capability(update = { copy(typeDefinitionProvider = value) }, register = {
    request(TypeDefinitionRequestType, handler)
})

fun LspServerCapabilitiesBuilder.implementationProvider(
    value: OrBoolean<ImplementationRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(ImplementationParams) -> Locations?,
): Unit = capability(update = { copy(implementationProvider = value) }, register = {
    request(Implementation.ImplementationRequest, handler)
})

fun LspServerCapabilitiesBuilder.referencesProvider(
    value: OrBoolean<ReferenceRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(ReferenceParams) -> List<Location>?,
): Unit = capability(update = { copy(referencesProvider = value) }, register = {
    request(ReferenceRequestType, handler)
})

fun LspServerCapabilitiesBuilder.documentHighlightProvider(
    value: OrBoolean<DocumentHighlightOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentHighlightProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentSymbolProvider(
    value: OrBoolean<DocumentSymbolRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(DocumentSymbolParams) -> List<DocumentSymbol>,
): Unit = capability(update = { copy(documentSymbolProvider = value) }, register = {
    request(DocumentSymbolRequest, handler)
})

fun LspServerCapabilitiesBuilder.codeActionProvider(
    codeActionKinds: List<CodeActionKind>,
    workDoneProgress: Boolean? = null,
    documentSelector: DocumentSelector? = null,
    resolveHandler: (suspend context(LspHandlerContext) CoroutineScope.(CodeAction) -> CodeAction)? = null,
    handler: suspend context(LspHandlerContext) CoroutineScope.(CodeActionParams) -> List<CommandOrCodeAction>?,
): Unit = capability(update = {
    copy(
        codeActionProvider = OrBoolean.of(
            CodeActionRegistrationOptions(
                codeActionKinds = codeActionKinds,
                resolveProvider = resolveHandler != null,
                workDoneProgress = workDoneProgress,
                documentSelector = documentSelector,
            )
        )
    )
}, register = {
    request(CodeActions.CodeActionRequest, handler)
    if (resolveHandler != null) {
        request(CodeActions.ResolveCodeAction, resolveHandler)
    }
})

fun LspServerCapabilitiesBuilder.codeLensProvider(
    documentSelector: DocumentSelector? = null,
    workDoneProgress: Boolean? = null,
    resolveHandler: (suspend context(LspHandlerContext) CoroutineScope.(CodeLens) -> CodeLens)? = null,
    handler: suspend context(LspHandlerContext) CoroutineScope.(CodeLensParams) -> List<CodeLens>?,
): Unit = capability(update = {
    copy(
        codeLensProvider = CodeLensRegistrationOptions(
            workDoneProgress = workDoneProgress,
            documentSelector = documentSelector,
            resolveProvider = resolveHandler != null,
        )
    )
}, register = {
    request(CodeLenses.CodeLensRequestType, handler)
    if (resolveHandler != null) {
        request(CodeLenses.ResolveCodeLens, resolveHandler)
    }
})

fun LspServerCapabilitiesBuilder.documentLinkProvider(
    value: DocumentLinkOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentLinkProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.colorProvider(
    value: OrBoolean<DocumentColorRegistrationOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(colorProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.documentFormattingProvider(
    value: OrBoolean<DocumentFormattingRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(DocumentFormattingParams) -> List<TextEdit>?,
): Unit = capability(update = { copy(documentFormattingProvider = value) }, register = {
    request(FormattingRequestType, handler)
})

fun LspServerCapabilitiesBuilder.documentRangeFormattingProvider(
    value: OrBoolean<DocumentRangeFormattingRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(DocumentRangeFormattingParams) -> List<TextEdit>?,
): Unit = capability(update = { copy(documentRangeFormattingProvider = value) }, register = {
    request(RangeFormattingRequestType, handler)
})

fun LspServerCapabilitiesBuilder.documentOnTypeFormattingProvider(
    value: DocumentOnTypeFormattingOptions,
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(documentOnTypeFormattingProvider = value) }, register = register)

/**
 * [prepareHandler] both enables `renameProvider.prepareProvider` and implements `textDocument/prepareRename`;
 * without it the capability stays the plain `true` that says "rename, but no prepare step".
 */
fun LspServerCapabilitiesBuilder.renameProvider(
    prepareHandler: (suspend context(LspHandlerContext) CoroutineScope.(PrepareRenameParams) -> PrepareRenameResult?)? = null,
    handler: suspend context(LspHandlerContext) CoroutineScope.(RenameParams) -> WorkspaceEdit?,
): Unit = capability(update = {
    copy(
        renameProvider = when (prepareHandler) {
            null -> OrBoolean(true)
            else -> OrBoolean.of(RenameRegistrationOptions(prepareProvider = true))
        }
    )
}, register = {
    request(RenameRequestType, handler)
    if (prepareHandler != null) {
        request(PrepareRenameRequestType, prepareHandler)
    }
})

fun LspServerCapabilitiesBuilder.foldingRangeProvider(
    handler: suspend context(LspHandlerContext) CoroutineScope.(FoldingRangeParams) -> List<FoldingRange>,
): Unit = capability(update = { copy(foldingRangeProvider = OrBoolean(true)) }, register = {
    request(FoldingRangeRequestType, handler)
})

fun LspServerCapabilitiesBuilder.executeCommandProvider(
    value: ExecuteCommandOptions,
    handler: suspend context(LspHandlerContext) CoroutineScope.(ExecuteCommandParams) -> JsonElement,
): Unit = capability(update = { copy(executeCommandProvider = value) }, register = {
    request(Commands.ExecuteCommand, handler)
})

fun LspServerCapabilitiesBuilder.selectionRangeProvider(
    value: OrBoolean<SelectionRangeOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(selectionRangeProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.linkedEditingRangeProvider(
    value: OrBoolean<LinkedEditingRangeOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(linkedEditingRangeProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.callHierarchyProvider(
    incomingCallsHandler: suspend context(LspHandlerContext) CoroutineScope.(CallHierarchyIncomingCallsParams) -> List<CallHierarchyIncomingCall>?,
    outgoingCallsHandler: suspend context(LspHandlerContext) CoroutineScope.(CallHierarchyOutgoingCallsParams) -> List<CallHierarchyOutgoingCall>?,
    prepareHandler: suspend context(LspHandlerContext) CoroutineScope.(CallHierarchyPrepareParams) -> List<CallHierarchyItem>?,
): Unit = capability(update = { copy(callHierarchyProvider = OrBoolean(true)) }, register = {
    request(CallHierarchyRequests.PrepareCallHierarchyRequestType, prepareHandler)
    request(CallHierarchyRequests.IncomingCallsRequestType, incomingCallsHandler)
    request(CallHierarchyRequests.OutgoingCallsRequestType, outgoingCallsHandler)
})

/**
 * `semanticTokensProvider.full` and `.range` are derived from [fullHandler] and [rangeHandler]; at least one
 * of them is required, since a semantic tokens capability that serves neither is unusable.
 */
fun LspServerCapabilitiesBuilder.semanticTokensProvider(
    legend: SemanticTokensLegend,
    fullHandler: (suspend context(LspHandlerContext) CoroutineScope.(SemanticTokensParams) -> SemanticTokens?)? = null,
    rangeHandler: (suspend context(LspHandlerContext) CoroutineScope.(SemanticTokensRangeParams) -> SemanticTokens)? = null,
) {
    require(fullHandler != null || rangeHandler != null) {
        "semanticTokensProvider requires a full or a range handler"
    }
    capability(update = {
        copy(
            semanticTokensProvider = SemanticTokensRegistrationOptions(
                legend = legend,
                range = if (rangeHandler != null) OrBoolean(true) else null,
                full = if (fullHandler != null) OrBoolean(true) else null,
            )
        )
    }, register = {
        if (fullHandler != null) {
            request(SemanticTokensRequests.SemanticTokensFullRequest, fullHandler)
        }
        if (rangeHandler != null) {
            request(SemanticTokensRequests.SemanticTokensRangeRequest, rangeHandler)
        }
    })
}

fun LspServerCapabilitiesBuilder.monikerProvider(
    value: OrBoolean<MonikerOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(monikerProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.typeHierarchyProvider(
    supertypesHandler: suspend context(LspHandlerContext) CoroutineScope.(TypeHierarchySupertypesParams) -> List<TypeHierarchyItem>?,
    subtypesHandler: suspend context(LspHandlerContext) CoroutineScope.(TypeHierarchySubtypesParams) -> List<TypeHierarchyItem>?,
    prepareHandler: suspend context(LspHandlerContext) CoroutineScope.(TypeHierarchyPrepareParams) -> List<TypeHierarchyItem>?,
): Unit = capability(update = { copy(typeHierarchyProvider = OrBoolean(true)) }, register = {
    request(TypeHierarchyRequests.PrepareTypeHierarchyRequestType, prepareHandler)
    request(TypeHierarchyRequests.SupertypesRequestType, supertypesHandler)
    request(TypeHierarchyRequests.SubtypesRequestType, subtypesHandler)
})

fun LspServerCapabilitiesBuilder.inlineValueProvider(
    value: OrBoolean<InlineValueOptions> = OrBoolean(true),
    register: LspHandlersBuilder.() -> Unit,
): Unit = capability(update = { copy(inlineValueProvider = value) }, register = register)

fun LspServerCapabilitiesBuilder.inlayHintProvider(
    documentSelector: DocumentSelector? = null,
    workDoneProgress: Boolean? = null,
    resolveHandler: (suspend context(LspHandlerContext) CoroutineScope.(InlayHint) -> InlayHint)? = null,
    handler: suspend context(LspHandlerContext) CoroutineScope.(InlayHintParams) -> List<InlayHint>?,
): Unit = capability(update = {
    copy(
        inlayHintProvider = OrBoolean.of(
            InlayHintRegistrationOptions(
                resolveProvider = resolveHandler != null,
                workDoneProgress = workDoneProgress,
                documentSelector = documentSelector,
            )
        )
    )
}, register = {
    request(InlayHints.InlayHintRequestType, handler)
    if (resolveHandler != null) {
        request(InlayHints.ResolveInlayHint, resolveHandler)
    }
})

fun LspServerCapabilitiesBuilder.diagnosticProvider(
    value: DiagnosticOptions,
    handler: suspend context(LspHandlerContext) CoroutineScope.(DocumentDiagnosticParams) -> DocumentDiagnosticReport,
): Unit = capability(update = { copy(diagnosticProvider = value) }, register = {
    request(Diagnostics.DocumentDiagnosticRequestType, handler)
})

fun LspServerCapabilitiesBuilder.workspaceSymbolProvider(
    value: OrBoolean<WorkspaceSymbolRegistrationOptions> = OrBoolean(true),
    handler: suspend context(LspHandlerContext) CoroutineScope.(WorkspaceSymbolParams) -> List<WorkspaceSymbol>,
): Unit = capability(update = { copy(workspaceSymbolProvider = value) }, register = {
    request(WorkspaceSymbolRequests.WorkspaceSymbolRequest, handler)
})

/**
 * `workspace.fileOperations.willRename`. Merges into [ServerWorkspaceCapabilities] rather than replacing it,
 * so it composes with the other `workspace` capabilities.
 */
fun LspServerCapabilitiesBuilder.willRenameFiles(
    filters: List<FileOperationFilter>,
    handler: suspend context(LspHandlerContext) CoroutineScope.(RenameFilesParams) -> WorkspaceEdit?,
): Unit = capability(update = {
    val workspace = workspace ?: ServerWorkspaceCapabilities()
    copy(
        workspace = workspace.copy(
            fileOperations = (workspace.fileOperations ?: FileOperations())
                .copy(willRename = FileOperationRegistrationOptions(filters = filters)),
        )
    )
}, register = {
    request(Workspace.WillRenameFiles, handler)
})
