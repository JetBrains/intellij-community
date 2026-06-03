package com.intellij.platform.lsp.impl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.containers.MultiMap
import org.eclipse.lsp4j.CallHierarchyRegistrationOptions
import org.eclipse.lsp4j.CodeActionRegistrationOptions
import org.eclipse.lsp4j.CodeLensRegistrationOptions
import org.eclipse.lsp4j.ColorProviderOptions
import org.eclipse.lsp4j.CompletionRegistrationOptions
import org.eclipse.lsp4j.DeclarationRegistrationOptions
import org.eclipse.lsp4j.DefinitionRegistrationOptions
import org.eclipse.lsp4j.DiagnosticRegistrationOptions
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions
import org.eclipse.lsp4j.DocumentFormattingRegistrationOptions
import org.eclipse.lsp4j.DocumentHighlightRegistrationOptions
import org.eclipse.lsp4j.DocumentLinkRegistrationOptions
import org.eclipse.lsp4j.DocumentOnTypeFormattingRegistrationOptions
import org.eclipse.lsp4j.DocumentRangeFormattingRegistrationOptions
import org.eclipse.lsp4j.DocumentSymbolRegistrationOptions
import org.eclipse.lsp4j.ExecuteCommandRegistrationOptions
import org.eclipse.lsp4j.FoldingRangeProviderOptions
import org.eclipse.lsp4j.HoverRegistrationOptions
import org.eclipse.lsp4j.ImplementationRegistrationOptions
import org.eclipse.lsp4j.InlayHintRegistrationOptions
import org.eclipse.lsp4j.InlineValueRegistrationOptions
import org.eclipse.lsp4j.LinkedEditingRangeRegistrationOptions
import org.eclipse.lsp4j.MonikerRegistrationOptions
import org.eclipse.lsp4j.ReferenceRegistrationOptions
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.SelectionRangeRegistrationOptions
import org.eclipse.lsp4j.SemanticTokensWithRegistrationOptions
import org.eclipse.lsp4j.SignatureHelpRegistrationOptions
import org.eclipse.lsp4j.TextDocumentChangeRegistrationOptions
import org.eclipse.lsp4j.TextDocumentRegistrationOptions
import org.eclipse.lsp4j.TextDocumentSaveRegistrationOptions
import org.eclipse.lsp4j.TypeDefinitionRegistrationOptions
import org.eclipse.lsp4j.TypeHierarchyRegistrationOptions
import org.eclipse.lsp4j.Unregistration
import org.eclipse.lsp4j.WorkspaceSymbolRegistrationOptions
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler

/**
 * Tracks dynamically registered IDE capabilities, which means handling the
 * [client/registerCapability](https://microsoft.github.io/language-server-protocol/specification/#client_registerCapability) and
 * [client/unregisterCapability](https://microsoft.github.io/language-server-protocol/specification/#client_unregisterCapability)
 * requests from the server.
 */
internal class LspDynamicCapabilities {
  private data class CapabilityInfo(
    val registrationId: String,
    val rawRegistrationOptions: JsonObject?,
    val lsp4jRegistrationOptions: Any?,
  )

  companion object {
    private val capabilityToLsp4jRegistrationOptionsClass = mutableMapOf<String, Class<*>>()

    // all `org.eclipse.lsp4j.*RegistrationOption` classes plus some classes without matching suffix
    // except unrelated NotebookDocumentSyncRegistrationOptions, StaticRegistrationOptions
    val prepareCallHierarchy = registration("textDocument/prepareCallHierarchy", CallHierarchyRegistrationOptions::class.java)
    val codeAction = registration("textDocument/codeAction", CodeActionRegistrationOptions::class.java)
    val codeLens = registration("textDocument/codeLens", CodeLensRegistrationOptions::class.java)
    val documentColor = registration("textDocument/documentColor", ColorProviderOptions::class.java)
    val completion = registration("textDocument/completion", CompletionRegistrationOptions::class.java)
    val declaration = registration("textDocument/declaration", DeclarationRegistrationOptions::class.java)
    val definition = registration("textDocument/definition", DefinitionRegistrationOptions::class.java)
    val diagnostic = registration("textDocument/diagnostic", DiagnosticRegistrationOptions::class.java)
    val didChangeWatchedFiles = registration("workspace/didChangeWatchedFiles", DidChangeWatchedFilesRegistrationOptions::class.java)
    val formatting = registration("textDocument/formatting", DocumentFormattingRegistrationOptions::class.java)
    val documentHighlight = registration("textDocument/documentHighlight", DocumentHighlightRegistrationOptions::class.java)
    val documentLink = registration("textDocument/documentLink", DocumentLinkRegistrationOptions::class.java)
    val onTypeFormatting = registration("textDocument/onTypeFormatting", DocumentOnTypeFormattingRegistrationOptions::class.java)
    val rangeFormatting = registration("textDocument/rangeFormatting", DocumentRangeFormattingRegistrationOptions::class.java)
    val documentSymbol = registration("textDocument/documentSymbol", DocumentSymbolRegistrationOptions::class.java)
    val foldingRange = registration("textDocument/foldingRange", FoldingRangeProviderOptions::class.java)
    val executeCommand = registration("workspace/executeCommand", ExecuteCommandRegistrationOptions::class.java)
    val hover = registration("textDocument/hover", HoverRegistrationOptions::class.java)
    val implementation = registration("textDocument/implementation", ImplementationRegistrationOptions::class.java)
    val inlayHint = registration("textDocument/inlayHint", InlayHintRegistrationOptions::class.java)
    val inlineValue = registration("textDocument/inlineValue", InlineValueRegistrationOptions::class.java)
    val linkedEditingRange = registration("textDocument/linkedEditingRange", LinkedEditingRangeRegistrationOptions::class.java)
    val moniker = registration("textDocument/moniker", MonikerRegistrationOptions::class.java)
    val references = registration("textDocument/references", ReferenceRegistrationOptions::class.java)
    val rename = registration("textDocument/rename", RenameOptions::class.java)
    val selectionRange = registration("textDocument/selectionRange", SelectionRangeRegistrationOptions::class.java)
    val semanticTokens = registration("textDocument/semanticTokens", SemanticTokensWithRegistrationOptions::class.java)
    val signatureHelp = registration("textDocument/signatureHelp", SignatureHelpRegistrationOptions::class.java)
    val didOpen = registration("textDocument/didOpen", TextDocumentRegistrationOptions::class.java)
    val didChange = registration("textDocument/didChange", TextDocumentChangeRegistrationOptions::class.java)
    val willSave = registration("textDocument/willSave", TextDocumentRegistrationOptions::class.java)
    val willSaveWaitUntil = registration("textDocument/willSaveWaitUntil", TextDocumentRegistrationOptions::class.java)
    val didClose = registration("textDocument/didClose", TextDocumentRegistrationOptions::class.java)
    val didSave = registration("textDocument/didSave", TextDocumentSaveRegistrationOptions::class.java)
    val typeDefinition = registration("textDocument/typeDefinition", TypeDefinitionRegistrationOptions::class.java)
    val prepareTypeHierarchy = registration("textDocument/prepareTypeHierarchy", TypeHierarchyRegistrationOptions::class.java)
    val symbol = registration("workspace/symbol", WorkspaceSymbolRegistrationOptions::class.java)

    private fun <T> registration(registrationCapability: String, registrationOptionsClass: Class<T>): Pair<String, Class<T>> {
      val result = registrationCapability to registrationOptionsClass
      capabilityToLsp4jRegistrationOptionsClass[registrationCapability] = registrationOptionsClass
      return result
    }
  }


  private val capabilityToInfo: MultiMap<String, CapabilityInfo> = MultiMap.createConcurrent()
  private val gson: Gson = MessageJsonHandler(emptyMap()).gson

  /**
   * Handles the [client/registerCapability](https://microsoft.github.io/language-server-protocol/specification/#client_registerCapability)
   * requests from the server.
   */
  fun registerCapability(registration: Registration) =
    capabilityToInfo.putValue(registration.method, CapabilityInfo(registration.id,
                                                                  registration.registerOptions as? JsonObject,
                                                                  getLsp4jRegistrationOptionsObject(registration)))

  /**
   * Handles the [client/unregisterCapability](https://microsoft.github.io/language-server-protocol/specification/#client_unregisterCapability)
   * requests from the server.
   */
  fun unregisterCapability(unregistration: Unregistration) {
    val registrations = capabilityToInfo.get(unregistration.method)
    registrations.removeIf { it.registrationId == unregistration.id }
    if (registrations.isEmpty()) {
      capabilityToInfo.remove(unregistration.method)
    }
  }

  private fun getLsp4jRegistrationOptionsObject(registration: Registration): Any? {
    val jsonObject = registration.registerOptions as? JsonObject ?: return null
    val clazz = capabilityToLsp4jRegistrationOptionsClass[registration.method] ?: return null
    try {
      return gson.fromJson(jsonObject, clazz)
    }
    catch (e: JsonSyntaxException) {
      thisLogger().warn("Failed to convert options of `${registration.method}` to ${clazz.simpleName} class: $e\n$jsonObject")
      return null
    }
  }

  /**
   * Tells whether the LSP server wants the IDE to use the [capability].
   *
   * Technically, it means that the [capability] has been passed from the server to the IDE as the
   * [Registration.method](https://microsoft.github.io/language-server-protocol/specification/#registration) field during the
   * [client/registerCapability](https://microsoft.github.io/language-server-protocol/specification/#client_registerCapability) request.
   *
   * [capability] examples: `workspace/didChangeWatchedFiles`, `textDocument/codeAction`, etc.
   */
  fun hasCapability(capability: String): Boolean = capabilityToInfo.containsKey(capability)

  /**
   * @see hasCapability
   */
  fun hasCapability(capabilityAndOptionsClass: Pair<String, Class<*>>): Boolean = hasCapability(capabilityAndOptionsClass.first)

  /**
   * Returns `org.eclipse.lsp4j.*RegistrationOption` objects associated with the capability.
   *
   * Each [client/registerCapability](https://microsoft.github.io/language-server-protocol/specification/#client_registerCapability)
   * request sent from the server to the IDE is accompanied by the corresponding
   * [Registration.registerOptions](https://microsoft.github.io/language-server-protocol/specification/#registration) field,
   * which is either `null`, or a specific `*RegistrationOption` class.
   *
   * Example: `workspace/didChangeWatchedFiles` capability options
   * are presented as a [DidChangeWatchedFilesRegistrationOptions] class instance.
   */
  fun <T> getCapabilityRegistrationOptions(capabilityAndOptionsClass: Pair<String, Class<T>>): List<T> =
    capabilityToInfo.get(capabilityAndOptionsClass.first)
      .mapNotNull { it.lsp4jRegistrationOptions }
      .filterIsInstance(capabilityAndOptionsClass.second)
}
