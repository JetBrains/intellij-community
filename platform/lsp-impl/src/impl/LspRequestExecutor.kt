package com.intellij.platform.lsp.impl

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient.Companion.DEFAULT_REQUEST_TIMEOUT_MS
import com.intellij.platform.lsp.impl.cache.LspCache
import com.intellij.platform.lsp.impl.cache.LspPerFileCache
import com.intellij.platform.lsp.impl.cache.LspSingleSlotCache
import com.intellij.platform.lsp.impl.cache.getOrCompute
import com.intellij.platform.lsp.impl.features.completion.createCompletionContext
import com.intellij.platform.lsp.impl.features.completion.toCompletionList
import com.intellij.platform.lsp.impl.features.documentSymbol.toDocumentSymbolTree
import com.intellij.platform.lsp.impl.features.documentation.HoverResultCache
import com.intellij.platform.lsp.impl.features.documentation.TextRangeAndMarkupContent
import com.intellij.platform.lsp.impl.features.highlighting.LspDocumentHighlightCache
import com.intellij.platform.lsp.impl.features.highlighting.TextRangeAndHighlightKind
import com.intellij.platform.lsp.impl.features.workspaceSymbol.toWorkspaceSymbol
import com.intellij.platform.lsp.impl.util.toLocationLink
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class LspRequestExecutor(
  private val lspClient: LspClientImpl,
  private val documentMapping: LspDocumentMapping,
) : LspRequestExecutorBase(lspClient) {

  private val allCaches = mutableListOf<LspCache>()

  private val hoverResultCache = register(HoverResultCache(lspClient.project))
  private val workspaceSymbolCache = register(LspSingleSlotCache<String, List<WorkspaceSymbol>>(lspClient.project))
  private val documentSymbolCache = register(LspPerFileCache<Unit, List<DocumentSymbol>>(lspClient.project))
  private val documentHighlightCache = register(LspDocumentHighlightCache(lspClient.project))
  private val selectionRangeCache = register(LspPerFileCache<Int, SelectionRange>(lspClient.project))

  private fun <T : LspCache> register(cache: T): T {
    allCaches.add(cache)
    return cache
  }

  override fun afterShutdown() {
    allCaches.forEach { it.clearCache() }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getCompletionList(file: VirtualFile, offset: Int, isAutoPopup: Boolean): CompletionList? =
    getCompletionListAsync(file, offset, isAutoPopup)
      .awaitSync()

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getCompletionListAsync(file: VirtualFile, offset: Int, isAutoPopup: Boolean): CompletableFuture<CompletionList?> {
    val host = documentMapping.unwrapInjection(file, offset) ?: return CompletableFuture.completedFuture(null)
    val completionContext = createCompletionContext(lspClient, host.hostDocument, host.hostOffset, isAutoPopup)
    return documentMapping.withDocumentAtOffset(host.hostFile, host.hostDocument, host.hostOffset) { lspDocument, position ->
      val params = CompletionParams(lspDocument.id, position, completionContext)
      val future = doSendRequestAsync { it.textDocumentService.completion(params) } ?: CompletableFuture.completedFuture(null)
      future.thenApply { lsp4jResponse -> lsp4jResponse?.toCompletionList() }
    } ?: CompletableFuture.completedFuture(null)
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getElementDefinitions(file: VirtualFile, offset: Int): List<LocationLink> {
    return documentMapping.withDocumentAtFileOffset(file, offset) { lspDocument, position ->
      val params = DefinitionParams(lspDocument.id, position)
      val lsp4jResponse = sendRequestSync { it.textDocumentService.definition(params) }
                          ?: return@withDocumentAtFileOffset emptyList()
      val locationLinks = lsp4jResponse.map({ items -> items.map { it.toLocationLink() } }, { it })
      locationLinks.map { documentMapping.findDocumentByUrl(it.targetUri)?.mapLocationLink(it) ?: it }.distinct()
    } ?: emptyList()
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getSignatureHelp(file: VirtualFile, offset: Int): SignatureHelp? {
    return documentMapping.withDocumentAtFileOffset(file, offset) { lspDocument, position ->
      val params = SignatureHelpParams(lspDocument.id, position)
      sendRequestSync { it.textDocumentService.signatureHelp(params) }
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  internal fun getTypeDefinitions(file: VirtualFile, offset: Int): List<LocationLink> {
    return documentMapping.withDocumentAtFileOffset(file, offset) { lspDocument, position ->
      val params = TypeDefinitionParams(lspDocument.id, position)
      val lsp4jResponse = sendRequestSync { it.textDocumentService.typeDefinition(params) }
                          ?: return@withDocumentAtFileOffset emptyList()
      val locationLinks = lsp4jResponse.map({ items -> items.map { it.toLocationLink() } }, { it })
      locationLinks.map { documentMapping.findDocumentByUrl(it.targetUri)?.mapLocationLink(it) ?: it }.distinct()
    } ?: emptyList()
  }

  @RequiresBackgroundThread
  internal fun getWorkspaceSymbolsCaching(query: String, timeoutMs: Int = DEFAULT_REQUEST_TIMEOUT_MS): List<WorkspaceSymbol>? {
    return workspaceSymbolCache.getOrCompute(query) {
      val params = WorkspaceSymbolParams(query)
      val response = sendRequestSync(timeoutMs) { it.workspaceService.symbol(params) }
                     ?: return@getOrCompute null
      response.map({ items -> items.map { it.toWorkspaceSymbol() } }, { it })
    }
  }

  @RequiresBackgroundThread
  internal fun getDocumentSymbolsCaching(file: VirtualFile): List<DocumentSymbol>? {
    return documentSymbolCache.getOrCompute(file) {
      val lspDocuments = documentMapping.getDocumentsInFileSync(file)
      val perDocument = lspDocuments.map { lspDocument ->
        val params = DocumentSymbolParams(lspDocument.id)
        val results = sendRequestSync { it.textDocumentService.documentSymbol(params) } ?: return@map null
        // lsp4j bug?
        // VSCode type is DocumentSymbol[] | SymbolInformation[],
        // but in lsp4j is (DocumentSymbol | SymbolInformation)[]
        val documentSymbols: List<DocumentSymbol> =
          if (results.firstOrNull()?.isLeft == true) toDocumentSymbolTree(results.mapNotNull { it.left })
          else results.mapNotNull { it.right }
        documentSymbols.map(lspDocument::mapDocumentSymbol)
      }
      perDocument.aggregatePerDocumentResults()
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  internal fun getDocumentHighlightsCaching(file: VirtualFile, offset: Int): List<TextRangeAndHighlightKind>? {
    return documentHighlightCache.getOrCompute(file, offset) {
      documentMapping.withDocumentAtFileOffset(file, offset) { lspDocument, position ->
        val params = DocumentHighlightParams(lspDocument.id, position)
        val highlights = sendRequestSync { it.textDocumentService.documentHighlight(params) }
                         ?: return@withDocumentAtFileOffset null
        val document = FileDocumentManager.getInstance().getDocument(file)
                       ?: return@withDocumentAtFileOffset null
        val hostMappedHighlights = highlights.map { DocumentHighlight(lspDocument.toHostRange(it.range), it.kind) }
        TextRangeAndHighlightKind.fromDocumentHighlights(hostMappedHighlights, document)
      }
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  internal fun getSelectionRangeCaching(file: VirtualFile, offset: Int): SelectionRange? {
    return selectionRangeCache.getOrCompute(file, offset) {
      documentMapping.withDocumentAtFileOffset(file, offset) { lspDocument, position ->
        val params = SelectionRangeParams(lspDocument.id, listOf(position))
        // Healthy servers respond instantly, typically much faster than 100 ms. A larger timeout may cause unwanted modal progress dialog
        val result: List<SelectionRange>? = sendRequestSync(timeoutMs = 100) {
          it.textDocumentService.selectionRange(params)
        }
        // SelectionRangeParams include exactly one Position, so the result should contain at most one SelectionRange
        result?.firstOrNull()?.let(lspDocument::mapSelectionRange)
      }
    }
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  fun getHoverCaching(file: VirtualFile, offset: Int, timeoutMs: Int = DEFAULT_REQUEST_TIMEOUT_MS): TextRangeAndMarkupContent? {
    return hoverResultCache.getOrCompute(file, offset) {
      val hover = documentMapping.withDocumentAtFileOffset(file, offset) { lspDocument, position ->
        val params = HoverParams(lspDocument.id, position)
        val hover = sendRequestSync(timeoutMs) { it.textDocumentService.hover(params) }
                    ?: return@withDocumentAtFileOffset null
        Hover().also {
          it.contents = hover.contents
          it.range = hover.range?.let { range -> lspDocument.toHostRange(range) }
        }
      } ?: return@getOrCompute null
      val document = FileDocumentManager.getInstance().getDocument(file)
      TextRangeAndMarkupContent.fromHover(hover, document, offset)
    }
  }
}
