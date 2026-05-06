package com.intellij.platform.lsp.common

import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeNotebookDocumentParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseNotebookDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenNotebookDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveNotebookDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.InlineValue
import org.eclipse.lsp4j.InlineValueParams
import org.eclipse.lsp4j.LinkedEditingRangeParams
import org.eclipse.lsp4j.LinkedEditingRanges
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.Moniker
import org.eclipse.lsp4j.MonikerParams
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.WorkDoneProgressCancelParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

@Suppress("PropertyName", "unused")
internal abstract class ServerSessionProtocolScope {

  // region Lifecycle
  val INITIALIZE = lspRequest<InitializeParams, InitializeResult>("initialize")
  val INITIALIZED = lspNotification<InitializedParams>("initialized")
  val SHUTDOWN = lspRequest<Unit, Any>("shutdown")
  val EXIT = lspNotification<Unit>("exit")
  val REGISTER_CAPABILITY = lspRequest<RegistrationParams, Void>("client/registerCapability") { c, p -> c.registerCapability(p) }
  val UNREGISTER_CAPABILITY = lspRequest<UnregistrationParams, Void>("client/unregisterCapability") { c, p -> c.unregisterCapability(p) }
  // endregion

  // region Document sync
  val DID_OPEN = lspNotification<DidOpenTextDocumentParams>("textDocument/didOpen")
  val DID_CHANGE = lspNotification<DidChangeTextDocumentParams>("textDocument/didChange")
  val DID_CLOSE = lspNotification<DidCloseTextDocumentParams>("textDocument/didClose")
  val DID_SAVE = lspNotification<DidSaveTextDocumentParams>("textDocument/didSave")
  val WILL_SAVE = lspNotification<WillSaveTextDocumentParams>("textDocument/willSave")
  val WILL_SAVE_WAIT_UNTIL = lspRequest<WillSaveTextDocumentParams, List<TextEdit>>("textDocument/willSaveWaitUntil")
  // endregion

  // region Notebook sync
  val NOTEBOOK_DID_OPEN = lspNotification<DidOpenNotebookDocumentParams>("notebookDocument/didOpen")
  val NOTEBOOK_DID_CHANGE = lspNotification<DidChangeNotebookDocumentParams>("notebookDocument/didChange")
  val NOTEBOOK_DID_SAVE = lspNotification<DidSaveNotebookDocumentParams>("notebookDocument/didSave")
  val NOTEBOOK_DID_CLOSE = lspNotification<DidCloseNotebookDocumentParams>("notebookDocument/didClose")
  // endregion

  // region Diagnostics
  val PUBLISH_DIAGNOSTICS = lspNotification<PublishDiagnosticsParams>("textDocument/publishDiagnostics") { c, p -> c.publishDiagnostics(p) }
  val DIAGNOSTIC = lspRequest<DocumentDiagnosticParams, DocumentDiagnosticReport>("textDocument/diagnostic")
  val WORKSPACE_DIAGNOSTIC = lspRequest<WorkspaceDiagnosticParams, WorkspaceDiagnosticReport>("workspace/diagnostic")
  val WORKSPACE_DIAGNOSTIC_REFRESH = lspRequest<Unit, Void>("workspace/diagnostic/refresh") { c, _ -> c.refreshDiagnostics() }
  // endregion

  // region Completion
  val COMPLETION = lspRequest<CompletionParams, Either<List<CompletionItem>, CompletionList>>("textDocument/completion")
  val COMPLETION_ITEM_RESOLVE = lspRequest<CompletionItem, CompletionItem>("completionItem/resolve")
  // endregion

  // region Navigation
  val DECLARATION = lspRequest<DeclarationParams, Either<List<Location>, List<LocationLink>>>("textDocument/declaration")
  val DEFINITION = lspRequest<DefinitionParams, Either<List<Location>, List<LocationLink>>>("textDocument/definition")
  val TYPE_DEFINITION = lspRequest<TypeDefinitionParams, Either<List<Location>, List<LocationLink>>>("textDocument/typeDefinition")
  val IMPLEMENTATION = lspRequest<ImplementationParams, Either<List<Location>, List<LocationLink>>>("textDocument/implementation")
  val REFERENCES = lspRequest<ReferenceParams, List<Location>>("textDocument/references")
  // endregion

  // region Hover & signature
  val HOVER = lspRequest<HoverParams, Hover>("textDocument/hover")
  val SIGNATURE_HELP = lspRequest<SignatureHelpParams, SignatureHelp>("textDocument/signatureHelp")
  // endregion

  // region Code actions
  val CODE_ACTION = lspRequest<CodeActionParams, List<Either<Command, CodeAction>>>("textDocument/codeAction")
  val CODE_ACTION_RESOLVE = lspRequest<CodeAction, CodeAction>("codeAction/resolve")
  // endregion

  // region Highlighting & symbols
  val DOCUMENT_HIGHLIGHT = lspRequest<DocumentHighlightParams, List<DocumentHighlight>>("textDocument/documentHighlight")
  val DOCUMENT_SYMBOL = lspRequest<DocumentSymbolParams, List<Either<SymbolInformation, DocumentSymbol>>>("textDocument/documentSymbol")
  val WORKSPACE_SYMBOL = lspRequest<WorkspaceSymbolParams, Either<List<SymbolInformation>, List<WorkspaceSymbol>>>("workspace/symbol")
  val WORKSPACE_SYMBOL_RESOLVE = lspRequest<WorkspaceSymbol, WorkspaceSymbol>("workspaceSymbol/resolve")
  // endregion

  // region Document links
  val DOCUMENT_LINK = lspRequest<DocumentLinkParams, List<DocumentLink>>("textDocument/documentLink")
  val DOCUMENT_LINK_RESOLVE = lspRequest<DocumentLink, DocumentLink>("documentLink/resolve")
  // endregion

  // region Semantic tokens
  val SEMANTIC_TOKENS_FULL = lspRequest<SemanticTokensParams, SemanticTokens>("textDocument/semanticTokens/full")
  val SEMANTIC_TOKENS_FULL_DELTA = lspRequest<SemanticTokensDeltaParams, Either<SemanticTokens, SemanticTokensDelta>>("textDocument/semanticTokens/full/delta")
  val SEMANTIC_TOKENS_RANGE = lspRequest<SemanticTokensRangeParams, SemanticTokens>("textDocument/semanticTokens/range")
  val SEMANTIC_TOKENS_REFRESH = lspRequest<Unit, Void>("workspace/semanticTokens/refresh") { c, _ -> c.refreshSemanticTokens() }
  // endregion

  // region Inlay hints
  val INLAY_HINT = lspRequest<InlayHintParams, List<InlayHint>>("textDocument/inlayHint")
  val INLAY_HINT_RESOLVE = lspRequest<InlayHint, InlayHint>("inlayHint/resolve")
  val INLAY_HINT_REFRESH = lspRequest<Unit, Void>("workspace/inlayHint/refresh") { c, _ -> c.refreshInlayHints() }
  // endregion

  // region Document colors
  val DOCUMENT_COLOR = lspRequest<DocumentColorParams, List<ColorInformation>>("textDocument/documentColor")
  val COLOR_PRESENTATION = lspRequest<ColorPresentationParams, List<ColorPresentation>>("textDocument/colorPresentation")
  // endregion

  // region Code lens
  val CODE_LENS = lspRequest<CodeLensParams, List<CodeLens>>("textDocument/codeLens")
  val CODE_LENS_RESOLVE = lspRequest<CodeLens, CodeLens>("codeLens/resolve")
  val CODE_LENS_REFRESH = lspRequest<Unit, Void>("workspace/codeLens/refresh") { c, _ -> c.refreshCodeLenses() }
  // endregion

  // region Formatting
  val FORMATTING = lspRequest<DocumentFormattingParams, List<TextEdit>>("textDocument/formatting")
  val RANGE_FORMATTING = lspRequest<DocumentRangeFormattingParams, List<TextEdit>>("textDocument/rangeFormatting")
  val ON_TYPE_FORMATTING = lspRequest<DocumentOnTypeFormattingParams, List<TextEdit>>("textDocument/onTypeFormatting")
  // endregion

  // region Rename
  val RENAME = lspRequest<RenameParams, WorkspaceEdit>("textDocument/rename")
  val PREPARE_RENAME = lspRequest<PrepareRenameParams, Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>>("textDocument/prepareRename")
  val LINKED_EDITING_RANGE = lspRequest<LinkedEditingRangeParams, LinkedEditingRanges>("textDocument/linkedEditingRange")
  // endregion

  // region Folding & selection
  val FOLDING_RANGE = lspRequest<FoldingRangeRequestParams, List<FoldingRange>>("textDocument/foldingRange")
  val SELECTION_RANGE = lspRequest<SelectionRangeParams, List<SelectionRange>>("textDocument/selectionRange")
  // endregion

  // region Type hierarchy
  val PREPARE_TYPE_HIERARCHY = lspRequest<TypeHierarchyPrepareParams, List<TypeHierarchyItem>>("textDocument/prepareTypeHierarchy")
  val TYPE_HIERARCHY_SUPERTYPES = lspRequest<TypeHierarchySupertypesParams, List<TypeHierarchyItem>>("typeHierarchy/supertypes")
  val TYPE_HIERARCHY_SUBTYPES = lspRequest<TypeHierarchySubtypesParams, List<TypeHierarchyItem>>("typeHierarchy/subtypes")
  // endregion

  // region Call hierarchy
  val PREPARE_CALL_HIERARCHY = lspRequest<CallHierarchyPrepareParams, List<CallHierarchyItem>>("textDocument/prepareCallHierarchy")
  val CALL_HIERARCHY_INCOMING_CALLS = lspRequest<CallHierarchyIncomingCallsParams, List<CallHierarchyIncomingCall>>("callHierarchy/incomingCalls")
  val CALL_HIERARCHY_OUTGOING_CALLS = lspRequest<CallHierarchyOutgoingCallsParams, List<CallHierarchyOutgoingCall>>("callHierarchy/outgoingCalls")
  // endregion

  // region Workspace
  val APPLY_EDIT = lspRequest<ApplyWorkspaceEditParams, ApplyWorkspaceEditResponse>("workspace/applyEdit") { c, p -> c.applyEdit(p) }
  val EXECUTE_COMMAND = lspRequest<ExecuteCommandParams, Any>("workspace/executeCommand")
  val CONFIGURATION = lspRequest<ConfigurationParams, List<Any>>("workspace/configuration") { c, p -> c.configuration(p) }
  val WORKSPACE_FOLDERS = lspRequest<Unit, List<WorkspaceFolder>>("workspace/workspaceFolders") { c, _ -> c.workspaceFolders() }
  val DID_CHANGE_CONFIGURATION = lspNotification<DidChangeConfigurationParams>("workspace/didChangeConfiguration")
  val DID_CHANGE_WATCHED_FILES = lspNotification<DidChangeWatchedFilesParams>("workspace/didChangeWatchedFiles")
  val DID_CHANGE_WORKSPACE_FOLDERS = lspNotification<DidChangeWorkspaceFoldersParams>("workspace/didChangeWorkspaceFolders")
  // endregion

  // region File operations
  val DID_CREATE_FILES = lspNotification<CreateFilesParams>("workspace/didCreateFiles")
  val DID_RENAME_FILES = lspNotification<RenameFilesParams>("workspace/didRenameFiles")
  val DID_DELETE_FILES = lspNotification<DeleteFilesParams>("workspace/didDeleteFiles")
  val WILL_CREATE_FILES = lspRequest<CreateFilesParams, WorkspaceEdit>("workspace/willCreateFiles")
  val WILL_RENAME_FILES = lspRequest<RenameFilesParams, WorkspaceEdit>("workspace/willRenameFiles")
  val WILL_DELETE_FILES = lspRequest<DeleteFilesParams, WorkspaceEdit>("workspace/willDeleteFiles")
  // endregion

  // region Window
  val SHOW_MESSAGE = lspNotification<MessageParams>("window/showMessage") { c, p -> c.showMessage(p) }
  val SHOW_MESSAGE_REQUEST = lspRequest<ShowMessageRequestParams, MessageActionItem>("window/showMessageRequest") { c, p -> c.showMessageRequest(p) }
  val SHOW_DOCUMENT = lspRequest<ShowDocumentParams, ShowDocumentResult>("window/showDocument") { c, p -> c.showDocument(p) }
  val LOG_MESSAGE = lspNotification<MessageParams>("window/logMessage") { c, p -> c.logMessage(p) }
  val TELEMETRY_EVENT = lspNotification<Any>("telemetry/event") { c, p -> c.telemetryEvent(p) }
  // endregion

  // region Progress & tracing
  val CREATE_PROGRESS = lspRequest<WorkDoneProgressCreateParams, Void>("window/workDoneProgress/create") { c, p -> c.createProgress(p) }
  val CANCEL_PROGRESS = lspNotification<WorkDoneProgressCancelParams>("window/workDoneProgress/cancel")
  val PROGRESS = lspNotification<ProgressParams>("$/progress") { c, p -> c.notifyProgress(p) }
  val SET_TRACE = lspNotification<SetTraceParams>("$/setTrace")
  val LOG_TRACE = lspNotification<LogTraceParams>("$/logTrace") { c, p -> c.logTrace(p) }
  // endregion

  // region Inline values
  val INLINE_VALUE = lspRequest<InlineValueParams, List<InlineValue>>("textDocument/inlineValue")
  val INLINE_VALUE_REFRESH = lspRequest<Unit, Void>("workspace/inlineValue/refresh") { c, _ -> c.refreshInlineValues() }
  // endregion

  // region Monikers
  val MONIKER = lspRequest<MonikerParams, List<Moniker>>("textDocument/moniker")
  // endregion
}

internal sealed class LspRequest<Params : Any, Response>(val method: String, val paramsClass: Class<Params>, val responseClass: Class<*>)
internal sealed class LspNotification<Params : Any>(val method: String, val paramsClass: Class<Params>)

internal class ServerToClientLspRequest<Params : Any, Response>(
  method: String,
  paramsClass: Class<Params>,
  responseClass: Class<*>,
  val send: (LanguageClient, Params) -> CompletableFuture<Response>,
) : LspRequest<Params, Response>(method, paramsClass, responseClass)

internal class ServerToClientLspNotification<Params : Any>(
  method: String,
  paramsClass: Class<Params>,
  val send: (LanguageClient, Params) -> Unit,
) : LspNotification<Params>(method, paramsClass)

internal class ClientToServerLspRequest<Params : Any, Response>(method: String, paramsClass: Class<Params>, responseClass: Class<*>) :
  LspRequest<Params, Response>(method, paramsClass, responseClass)

internal class ClientToServerLspNotification<Params : Any>(method: String, paramsClass: Class<Params>) :
  LspNotification<Params>(method, paramsClass)

internal inline fun <reified P : Any, reified R> lspRequest(method: String) =
  ClientToServerLspRequest<P, R>(method, P::class.java, R::class.java)

internal inline fun <reified P : Any> lspNotification(method: String) =
  ClientToServerLspNotification(method, P::class.java)

internal inline fun <reified P : Any, reified R> lspRequest(method: String, noinline send: (LanguageClient, P) -> CompletableFuture<R>) =
  ServerToClientLspRequest(method, P::class.java, R::class.java, send)

internal inline fun <reified P : Any> lspNotification(method: String, noinline send: (LanguageClient, P) -> Unit) =
  ServerToClientLspNotification(method, P::class.java, send)
