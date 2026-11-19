// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.integrationTests.fakeServer

import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.CompletableFuture

/**
 * The LSP methods that a test names in its expectations.
 *
 * A copy of a part of `ServerSessionProtocolScope` of `intellij.platform.lsp.impl.tests`: the declarations there are internal,
 * and that module brings the unit test framework to the classpath.
 */
@Suppress("PropertyName")
internal abstract class ServerSessionProtocolScope {
  val REGISTER_CAPABILITY = lspRequest<RegistrationParams, Void>("client/registerCapability") { c, p -> c.registerCapability(p) }
  val UNREGISTER_CAPABILITY = lspRequest<UnregistrationParams, Void>("client/unregisterCapability") { c, p -> c.unregisterCapability(p) }

  val DID_OPEN = lspNotification<DidOpenTextDocumentParams>("textDocument/didOpen")
  val DID_CHANGE = lspNotification<DidChangeTextDocumentParams>("textDocument/didChange")
  val DID_CLOSE = lspNotification<DidCloseTextDocumentParams>("textDocument/didClose")
  val DID_SAVE = lspNotification<DidSaveTextDocumentParams>("textDocument/didSave")
  val WILL_SAVE = lspNotification<WillSaveTextDocumentParams>("textDocument/willSave")
  val WILL_SAVE_WAIT_UNTIL = lspRequest<WillSaveTextDocumentParams, List<TextEdit>>("textDocument/willSaveWaitUntil")

  val DOCUMENT_SYNC: Set<LspMethod> = setOf(DID_OPEN, DID_CHANGE, DID_CLOSE, DID_SAVE, WILL_SAVE, WILL_SAVE_WAIT_UNTIL)

  val PUBLISH_DIAGNOSTICS = lspNotification<PublishDiagnosticsParams>("textDocument/publishDiagnostics") { c, p -> c.publishDiagnostics(p) }
  val DIAGNOSTIC = lspRequest<DocumentDiagnosticParams, DocumentDiagnosticReport>("textDocument/diagnostic")
  val WORKSPACE_DIAGNOSTIC_REFRESH = lspRequest<Unit, Void>("workspace/diagnostic/refresh") { c, _ -> c.refreshDiagnostics() }

  val DOCUMENT_HIGHLIGHT = lspRequest<DocumentHighlightParams, List<DocumentHighlight>>("textDocument/documentHighlight")

  val DOCUMENT_LINK = lspRequest<DocumentLinkParams, List<DocumentLink>>("textDocument/documentLink")
  val DOCUMENT_LINK_RESOLVE = lspRequest<DocumentLink, DocumentLink>("documentLink/resolve")

  val SEMANTIC_TOKENS_FULL = lspRequest<SemanticTokensParams, SemanticTokens>("textDocument/semanticTokens/full")
  val SEMANTIC_TOKENS_FULL_DELTA = lspRequest<SemanticTokensDeltaParams, Either<SemanticTokens, SemanticTokensDelta>>("textDocument/semanticTokens/full/delta")
  val SEMANTIC_TOKENS_RANGE = lspRequest<SemanticTokensRangeParams, SemanticTokens>("textDocument/semanticTokens/range")
  val SEMANTIC_TOKENS_REFRESH = lspRequest<Unit, Void>("workspace/semanticTokens/refresh") { c, _ -> c.refreshSemanticTokens() }
}

internal sealed interface LspMethod {
  val method: String
}

internal sealed class LspRequest<Params : Any, Response>(override val method: String) : LspMethod
internal sealed class LspNotification<Params : Any>(override val method: String) : LspMethod

internal class ServerToClientLspRequest<Params : Any, Response>(
  method: String,
  val send: (LanguageClient, Params) -> CompletableFuture<Response>,
) : LspRequest<Params, Response>(method)

internal class ServerToClientLspNotification<Params : Any>(
  method: String,
  val send: (LanguageClient, Params) -> Unit,
) : LspNotification<Params>(method)

internal class ClientToServerLspRequest<Params : Any, Response>(method: String) : LspRequest<Params, Response>(method)

internal class ClientToServerLspNotification<Params : Any>(method: String) : LspNotification<Params>(method)

internal fun <P : Any, R> lspRequest(method: String) = ClientToServerLspRequest<P, R>(method)

internal fun <P : Any> lspNotification(method: String) = ClientToServerLspNotification<P>(method)

internal fun <P : Any, R> lspRequest(method: String, send: (LanguageClient, P) -> CompletableFuture<R>) =
  ServerToClientLspRequest(method, send)

internal fun <P : Any> lspNotification(method: String, send: (LanguageClient, P) -> Unit) =
  ServerToClientLspNotification(method, send)
