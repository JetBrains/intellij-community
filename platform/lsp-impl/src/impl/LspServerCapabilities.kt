package com.intellij.platform.lsp.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import org.eclipse.lsp4j.SaveOptions
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.TextDocumentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.jetbrains.annotations.ApiStatus

internal val LspClientImpl.textDocumentSyncKind: TextDocumentSyncKind?
  get() = serverCapabilities?.textDocumentSync?.map({ it }, { it.change })

@get:ApiStatus.Internal
val LspClient.isNotebookSupportedByServer: Boolean
  get() = (this as LspClientImpl).serverCapabilities?.notebookDocumentSync != null

internal fun LspClientImpl.supportsPullDiagnostics(file: VirtualFile): Boolean =
  serverCapabilities?.diagnosticProvider != null ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.diagnostic)

internal fun LspClientImpl.supportsDocumentColor(file: VirtualFile): Boolean =
  serverCapabilities?.colorProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentColor)

internal fun LspClientImpl.supportsDocumentLink(file: VirtualFile): Boolean =
  serverCapabilities?.documentLinkProvider != null ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentLink)

internal fun LspClientImpl.supportsDocumentSymbol(file: VirtualFile): Boolean =
  serverCapabilities?.documentSymbolProvider != null ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentSymbol)

internal fun LspClientImpl.supportsFoldingRange(file: VirtualFile): Boolean =
  serverCapabilities?.foldingRangeProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.foldingRange)

/**
 * A capability without listed kinds supports every kind. For it, the IDE does not call [codeActionKindsFilter].
 * The static capabilities do not veto a dynamic registration. Every dynamic registration that matches [file] counts.
 */
internal fun LspClientImpl.supportsCodeActions(file: VirtualFile, codeActionKindsFilter: (List<String>) -> Boolean): Boolean {
  val provider = serverCapabilities?.codeActionProvider
  val staticSupport = when {
    provider == null -> false
    provider.isLeft -> provider.left == true
    else -> provider.right?.codeActionKinds?.let(codeActionKindsFilter) != false
  }
  if (staticSupport) return true
  return getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.codeAction)
    .any { it.codeActionKinds?.let(codeActionKindsFilter) != false }
}

/** A `CodeAction` has no document URI. So every dynamic registration counts, whatever its document selector. */
internal fun LspClientImpl.hasCodeActionResolveProvider(): Boolean =
  serverCapabilities?.codeActionProvider?.right?.resolveProvider == true ||
  dynamicCapabilities.getCapabilityRegistrationOptions(LspDynamicCapabilities.codeAction).any { it.resolveProvider == true }

internal fun LspClientImpl.supportsCompletion(file: VirtualFile): Boolean =
  serverCapabilities?.completionProvider != null ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.completion)

internal fun LspClientImpl.getCompletionTriggerCharacters(file: VirtualFile): List<String>? {
  val dynamicTriggerCharacters = getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.completion)
    .flatMap { it.triggerCharacters.orEmpty() }
    .ifEmpty { null }

  return dynamicTriggerCharacters ?: serverCapabilities?.completionProvider?.triggerCharacters
}

internal fun LspClientImpl.supportsCompletionResolve(file: VirtualFile): Boolean =
  serverCapabilities?.completionProvider?.resolveProvider
  ?: getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.completion).any { it.resolveProvider == true }

internal fun LspClientImpl.supportsCodeLensResolve(file: VirtualFile): Boolean =
  serverCapabilities?.codeLensProvider?.resolveProvider
  ?: getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.codeLens).any { it.resolveProvider == true }

internal fun LspClientImpl.getSemanticTokensFullOptions(file: VirtualFile): SemanticTokensWithRegistrationOptions? {
  val candidates = buildList {
    addAll(getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.semanticTokens))
    serverCapabilities?.semanticTokensProvider?.let { add(it) }
  }
  return candidates.firstOrNull { options -> options.full?.let { it.left ?: true } == true }
}

internal fun LspClientImpl.supportsGotoDefinition(file: VirtualFile): Boolean =
  serverCapabilities?.definitionProvider?.let { it.left ?: true } == true ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.definition)

internal fun LspClientImpl.supportsGotoTypeDefinition(file: VirtualFile): Boolean =
  serverCapabilities?.typeDefinitionProvider?.let { it.left ?: true } == true ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.typeDefinition)

internal fun LspClientImpl.supportsGotoImplementation(file: VirtualFile): Boolean =
  serverCapabilities?.implementationProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.implementation)

internal fun LspClientImpl.supportsHover(file: VirtualFile): Boolean =
  serverCapabilities?.hoverProvider?.let { it.left ?: true } == true ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.hover)

/** True when the server provides the text for [scheme] URIs, through the `workspace/textDocumentContent` request. */
internal fun LspClientImpl.providesTextDocumentContent(scheme: String?): Boolean =
  scheme != null && serverCapabilities?.workspace?.textDocumentContent?.schemes?.contains(scheme) == true

internal fun LspClientImpl.supportsFindReferences(file: VirtualFile): Boolean =
  serverCapabilities?.referencesProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.references)

internal fun LspClientImpl.supportsInlayHints(file: VirtualFile): Boolean =
  serverCapabilities?.inlayHintProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.inlayHint)

internal fun LspClientImpl.supportsDocumentHighlights(file: VirtualFile): Boolean =
  serverCapabilities?.documentHighlightProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.documentHighlight)

internal fun LspClientImpl.supportsGoToSymbol(): Boolean = serverCapabilities?.workspaceSymbolProvider?.let { it.left ?: true } == true
                                                           || dynamicCapabilities.hasCapability(LspDynamicCapabilities.symbol)

internal fun LspClientImpl.supportsSignatureHelp(file: VirtualFile): Boolean =
  serverCapabilities?.signatureHelpProvider != null ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.signatureHelp)

internal fun LspClientImpl.supportsCallHierarchy(file: VirtualFile): Boolean =
  serverCapabilities?.callHierarchyProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.prepareCallHierarchy)

internal fun LspClientImpl.supportsTypeHierarchy(file: VirtualFile): Boolean =
  serverCapabilities?.typeHierarchyProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.prepareTypeHierarchy)

internal fun LspClientImpl.supportsSelectionRange(file: VirtualFile): Boolean =
  serverCapabilities?.selectionRangeProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.selectionRange)

internal fun LspClientImpl.supportsCodeLens(file: VirtualFile): Boolean =
  serverCapabilities?.codeLensProvider != null ||
  hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.codeLens)

internal fun LspClientImpl.supportsRename(file: VirtualFile): Boolean =
  serverCapabilities?.renameProvider?.let { it.left ?: true }
  ?: hasDynamicCapabilityToHandleThisFile(file, LspDynamicCapabilities.rename)

internal fun LspClientImpl.supportsPrepareRename(file: VirtualFile): Boolean {
  return serverCapabilities?.renameProvider?.right?.prepareProvider == true ||
         getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.rename).any { it.prepareProvider == true }
}

/**
 * A server does not have to list its code action kinds; no listed kinds means every kind.
 * The static capabilities do not veto a dynamic registration: the kind is supported when either source covers it.
 * Every dynamic registration matching the file counts, because a server may split its kinds across registrations.
 */
internal fun LspClientImpl.supportsCodeActionsOfKind(file: VirtualFile, kind: String): Boolean {
  val provider = serverCapabilities?.codeActionProvider
  val staticSupport = when {
    provider == null -> false
    provider.isLeft -> provider.left == true
    else -> provider.right?.codeActionKinds?.matchesCodeActionKind(kind) != false
  }
  if (staticSupport) return true
  return getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.codeAction)
    .any { it.codeActionKinds?.matchesCodeActionKind(kind) != false }
}

/**
 * Unlike [supportsCodeActionsOfKind], the server must list [kind] or a child of [kind].
 * For example, `source.organizeImports.ts` is a child of `source.organizeImports`.
 * A parent kind, `codeActionProvider: true`, and a capability without `codeActionKinds` do not count.
 */
internal fun LspClientImpl.supportsExplicitlyCodeActionOfKind(file: VirtualFile, kind: String): Boolean =
  serverCapabilities?.codeActionProvider?.right?.codeActionKinds?.containsCodeActionKindOrChild(kind) == true ||
  getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.codeAction)
    .any { it.codeActionKinds?.containsCodeActionKindOrChild(kind) == true }

// a kind covers its own dot-separated subtree only: `refactor.extract.variable2` is not under `refactor.extract.variable`
private fun List<String>.matchesCodeActionKind(kind: String): Boolean =
  any { it == kind || kind.startsWith("$it.") || it.startsWith("$kind.") }

private fun List<String>.containsCodeActionKindOrChild(kind: String): Boolean =
  any { it == kind || it.startsWith("$kind.") }

internal fun LspClientImpl.getDidSaveOptions(file: VirtualFile): SaveOptions? {
  val textDocumentSync = serverCapabilities?.textDocumentSync
  if (textDocumentSync?.right?.save?.left == true) return SaveOptions(false)
  textDocumentSync?.right?.save?.right?.let { return it }
  // According to https://microsoft.github.io/language-server-protocol/specification/#textDocument_synchronization
  // `textDocumentSync` can be `TextDocumentSyncOptions` or `TextDocumentSyncKind`. When it is `TextDocumentSyncKind` there are no rules
  // that the client must send didSave notification, but VS Code does it in the case of TextDocumentSyncKind.Incremental and
  // TextDocumentSyncKind.Full. As good practice we should follow VS Code behavior.
  if (textDocumentSync?.left != null && textDocumentSync.left != TextDocumentSyncKind.None) return SaveOptions(false)
  val dynamicOptions = getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.didSave)
  if (dynamicOptions.isNotEmpty()) return SaveOptions(dynamicOptions.any { it.includeText == true })
  return null
}

/**
 * @return `true` if the server says it is able to format at least some files;
 * `false` if the server doesn't support code formatting at all
 * @see doesServerExplicitlyWantToFormatThisFile
 */
internal fun LspClientImpl.hasFullFileFormattingCapability(): Boolean =
  dynamicCapabilities.hasCapability(LspDynamicCapabilities.formatting) ||
  serverCapabilities?.documentFormattingProvider?.let { it.left ?: true } == true


/**
 * @return `true` if the server says it is able to format at least some files;
 * `false` if the server doesn't support code range formatting at all
 * @see doesServerExplicitlyWantToFormatThisFile
 */
internal fun LspClientImpl.hasRangeFormattingCapability(): Boolean =
  dynamicCapabilities.hasCapability(LspDynamicCapabilities.rangeFormatting) ||
  serverCapabilities?.documentRangeFormattingProvider?.let { it.left ?: true } == true

/**
 * See docs for the `serverExplicitlyWantsToFormatThisFile` parameter in
 * [com.intellij.platform.lsp.api.customization.LspFormattingSupport.shouldFormatThisFileExclusivelyByServer]
 */
internal fun LspClientImpl.doesServerExplicitlyWantToFormatThisFile(file: VirtualFile, isFullFileFormatting: Boolean): Boolean {
  @Suppress("UNCHECKED_CAST")
  val capabilityAndOptionsClass: Pair<String, Class<TextDocumentRegistrationOptions>> =
    (if (isFullFileFormatting) LspDynamicCapabilities.formatting else LspDynamicCapabilities.rangeFormatting)
      as Pair<String, Class<TextDocumentRegistrationOptions>>
  // We intentionally don't check static server capabilities (serverCapabilities),
  // which can only say true/false but can't answer whether THIS specific file should be formatted exclusively by the server.
  // Erroneous `true` answer is very dangerous as it disables the IDE's internal formatter.
  return hasDynamicCapabilityToHandleThisFile(file, capabilityAndOptionsClass)
}

internal fun LspClientImpl.getSignatureHelpTriggerCharacters(file: VirtualFile): List<String>? {
  val staticTriggerCharacters = serverCapabilities?.signatureHelpProvider?.triggerCharacters
  val dynamicOptions = getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.signatureHelp)
  val dynamicTriggerCharacters = dynamicOptions.flatMap { it.triggerCharacters.orEmpty() }
  return when {
    dynamicTriggerCharacters.isNotEmpty() -> dynamicTriggerCharacters
    staticTriggerCharacters != null -> staticTriggerCharacters
    else -> null
  }
}

internal fun LspClientImpl.getOnTypeFormattingTriggerCharacters(file: VirtualFile): List<String>? {
  val staticOptions = serverCapabilities?.documentOnTypeFormattingProvider
  val staticTriggerChars = staticOptions?.let {
    buildList {
      add(it.firstTriggerCharacter)
      it.moreTriggerCharacter?.let { chars -> addAll(chars) }
    }
  }
  val dynamicOptions = getDynamicCapabilityOptionsListForFile(file, LspDynamicCapabilities.onTypeFormatting)
  val dynamicTriggerChars = dynamicOptions.flatMap {
    buildList {
      add(it.firstTriggerCharacter)
      it.moreTriggerCharacter?.let { chars -> addAll(chars) }
    }
  }.ifEmpty { null }
  return dynamicTriggerChars ?: staticTriggerChars
}

private fun <T : TextDocumentRegistrationOptions> LspClientImpl.hasDynamicCapabilityToHandleThisFile(
  file: VirtualFile,
  capabilityAndOptionsClass: Pair<String, Class<T>>,
): Boolean = getDynamicCapabilityOptionsListForFile(file, capabilityAndOptionsClass).isNotEmpty()

/** Every registration of the capability whose document selector matches [file]; a registration without a selector matches any file. */
private fun <T : TextDocumentRegistrationOptions> LspClientImpl.getDynamicCapabilityOptionsListForFile(
  file: VirtualFile,
  capabilityAndOptionsClass: Pair<String, Class<T>>,
): List<T> {
  if (file.isDirectory) {
    logWarn("Directory not expected here. Capability: ${capabilityAndOptionsClass.first}, file: ${file.path}")
    return emptyList()
  }
  return dynamicCapabilities.getCapabilityRegistrationOptions(capabilityAndOptionsClass).filter { documentSelectorMatches(it, file) }
}

private fun LspClientImpl.documentSelectorMatches(registrationOptions: TextDocumentRegistrationOptions, file: VirtualFile): Boolean {
  val documentSelector = registrationOptions.documentSelector ?: return true
  for (filter in documentSelector) {
    if (filter.scheme != null && filter.scheme != "file") continue

    val language = filter.language
    val filterPattern = filter.pattern
    if (filterPattern != null && filterPattern.isRight) {
      // A RelativePattern needs its baseUri resolved against the workspace folders. The IDE does not support it yet.
      logWarn("Ignoring the document filter, its pattern is relative: ${filterPattern.right}")
      continue
    }
    val pattern = filterPattern?.left
    if (language == null && pattern == null) continue
    if (language != null && language != descriptor.getLanguageId(file)) continue
    if (pattern != null && !globMatcher.pathMatches(file.path, false, pattern, null)) continue

    return true
  }
  return false
}
