// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.lineMarkers

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspGoToImplementationSupport
import com.intellij.platform.lsp.api.customization.LspInheritanceMarker
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkerKind
import com.intellij.platform.lsp.api.customization.LspInheritanceMarkersSupport
import com.intellij.platform.lsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspDocument
import com.intellij.platform.lsp.impl.aggregateToPullResult
import com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing
import com.intellij.platform.lsp.impl.features.documentSymbol.toDocumentSymbols
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SymbolKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Pulls the inheritance markers for a file.
 *
 * Traffic gates keep the per-symbol request count low:
 * - An interface member is queried always. It is where a marker is the most likely.
 * - A class method is queried only after `typeHierarchy/subtypes` confirmed that the class has subtypes,
 *   because the method's overriders can live only in the subtypes.
 * - A top-level function, and a method of any other container, are not queried.
 */
internal class LspInheritanceMarkersCache(
  private val lspClient: LspClientImpl,
) : LspHighlightingCache<LspInheritanceMarker>(lspClient.project) {

  private val capLoggedFiles = mutableSetOf<VirtualFile>()

  /**
   * One pull costs one `documentSymbol` request plus one request per queried symbol,
   * so the delay is longer than [LOW_PRIORITY_QUIESCENCE_DELAY].
   */
  override val quiescenceDelay: Duration
    get() = Registry.intValue("lsp.inheritance.markers.quiescence.ms", 1000).milliseconds

  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val customizer = lspClient.descriptor.lspCustomization.inheritanceMarkersCustomizer
    return customizer is LspInheritanceMarkersSupport &&
           customizer.shouldAskServerForMarkers(file) &&
           lspClient.supportsDocumentSymbol(file) &&
           (implementationRequestsAllowed(file) || typeHierarchyRequestsAllowed(file))
  }

  /**
   * The per-symbol requests respect the customizers that gate the same requests for the other features.
   */
  private fun implementationRequestsAllowed(file: VirtualFile): Boolean =
    lspClient.supportsGotoImplementation(file) &&
    lspClient.descriptor.lspCustomization.goToImplementationCustomizer is LspGoToImplementationSupport

  private fun typeHierarchyRequestsAllowed(file: VirtualFile): Boolean =
    lspClient.supportsTypeHierarchy(file) &&
    lspClient.descriptor.lspCustomization.typeHierarchyCustomizer is LspTypeHierarchySupport

  override suspend fun sendRequest(file: VirtualFile): LspPullResult<LspInheritanceMarker> {
    val customizer = lspClient.descriptor.lspCustomization.inheritanceMarkersCustomizer as? LspInheritanceMarkersSupport
                     ?: return LspPullResult.Failed
    val implementationAllowed = implementationRequestsAllowed(file)
    val typeHierarchyAllowed = typeHierarchyRequestsAllowed(file)

    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentSymbolParams(lspDocument.id)
      val response = lspClient.sendRequest { it.textDocumentService.documentSymbol(params) }
                     ?: return@forEachDocumentInFile null

      val scope = selectSymbols(customizer, response.toDocumentSymbols(), implementationAllowed, typeHierarchyAllowed)
      val candidateCount = scope.interfaceMembers.size + scope.containers.size + scope.classMembers.size
      if (candidateCount > Registry.intValue("lsp.inheritance.markers.max.symbols", 200)) {
        logCapSkip(file, candidateCount)
        return@forEachDocumentInFile emptyList()
      }

      computeMarkers(lspDocument, scope)
    }
    return perDocument.aggregateToPullResult()
  }

  private fun selectSymbols(
    customizer: LspInheritanceMarkersSupport,
    roots: List<DocumentSymbol>,
    implementationAllowed: Boolean,
    typeHierarchyAllowed: Boolean,
  ): SymbolScope {
    val allSymbols = flattenWithParent(roots)
    val interfaceMembers = if (implementationAllowed) {
      allSymbols.filter { it.parent?.kind == SymbolKind.Interface && customizer.isMethodLikeSymbol(it.symbol.kind) }
    }
    else emptyList()
    val containers = if (typeHierarchyAllowed) {
      allSymbols.filter { customizer.isClassLikeSymbol(it.symbol.kind) }
    }
    else emptyList()
    val classMembers = if (implementationAllowed && typeHierarchyAllowed) {
      allSymbols.filter {
        val parent = it.parent
        parent != null && parent.kind != SymbolKind.Interface &&
        customizer.isClassLikeSymbol(parent.kind) && customizer.isMethodLikeSymbol(it.symbol.kind)
      }
    }
    else emptyList()
    return SymbolScope(interfaceMembers, containers, classMembers)
  }

  private suspend fun computeMarkers(
    lspDocument: LspDocument,
    scope: SymbolScope,
  ): List<Pair<Range, LspInheritanceMarker>> {
    val semaphore = Semaphore(MAX_PARALLEL_REQUESTS)
    return coroutineScope {
      val work = ArrayList<Deferred<List<Pair<Range, LspInheritanceMarker>>>>()

      for ((symbol, parent) in scope.interfaceMembers) {
        work.add(async { listOfNotNull(computeMethodMarker(lspDocument, symbol, parent?.kind, semaphore)) })
      }

      val classMembersByContainer = scope.classMembers.groupBy { it.parent }
      for ((containerSymbol, _) in scope.containers) {
        work.add(async {
          val classMarker = semaphore.withPermit { computeSafely { computeClassDownMarker(lspDocument, containerSymbol) } }
                            ?: return@async emptyList()
          val result = ArrayList<Pair<Range, LspInheritanceMarker>>()
          result.add(classMarker)
          // The container has subtypes, so its methods can have overriders.
          val members = classMembersByContainer[containerSymbol].orEmpty()
          coroutineScope {
            members.map { (symbol, parent) ->
              async { computeMethodMarker(lspDocument, symbol, parent?.kind, semaphore) }
            }.awaitAll().filterNotNull().forEach(result::add)
          }
          result
        })
      }

      work.awaitAll().flatten()
    }
  }

  private suspend fun computeMethodMarker(
    lspDocument: LspDocument,
    symbol: DocumentSymbol,
    parentKind: SymbolKind?,
    semaphore: Semaphore,
  ): Pair<Range, LspInheritanceMarker>? = semaphore.withPermit {
    computeSafely { computeMethodDownMarker(lspDocument, symbol, parentKind) }
  }

  /**
   * Isolates one marker computation, so one failure does not cancel the sibling computations.
   */
  private suspend fun <T> computeSafely(compute: suspend () -> T?): T? =
    try {
      compute()
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      thisLogger().warn("LSP inheritance marker computation failed", e)
      null
    }

  private suspend fun computeMethodDownMarker(
    lspDocument: LspDocument,
    symbol: DocumentSymbol,
    parentKind: SymbolKind?,
  ): Pair<Range, LspInheritanceMarker>? {
    val targets = lspClient.requestImplementationTargets(lspDocument, symbol.selectionRange.start) ?: return null
    val kind = if (parentKind == SymbolKind.Interface) LspInheritanceMarkerKind.IMPLEMENTED_METHOD
               else LspInheritanceMarkerKind.OVERRIDDEN_METHOD
    return createMarker(lspDocument, symbol, kind, targets)
  }

  private suspend fun computeClassDownMarker(lspDocument: LspDocument, symbol: DocumentSymbol): Pair<Range, LspInheritanceMarker>? {
    val targets = lspClient.requestSubtypeTargets(lspDocument, symbol.selectionRange.start) ?: return null
    val kind = if (symbol.kind == SymbolKind.Interface) LspInheritanceMarkerKind.IMPLEMENTED_CLASS
               else LspInheritanceMarkerKind.SUBCLASSED_CLASS
    return createMarker(lspDocument, symbol, kind, targets)
  }

  private fun createMarker(
    lspDocument: LspDocument,
    symbol: DocumentSymbol,
    kind: LspInheritanceMarkerKind,
    targets: List<Location>,
  ): Pair<Range, LspInheritanceMarker>? {
    val hostAnchorRange = lspDocument.toHostRange(symbol.selectionRange)
    val hostTargets = lspClient.mapTargetsToHost(lspDocument, hostAnchorRange.start, targets)
    if (hostTargets.isEmpty()) return null
    return hostAnchorRange to LspInheritanceMarker(kind, symbol.name, symbol.kind, hostTargets)
  }

  private fun logCapSkip(file: VirtualFile, symbolCount: Int) {
    val firstTime = synchronized(capLoggedFiles) { capLoggedFiles.add(file) }
    if (firstTime) {
      thisLogger().info("Skipped the LSP inheritance markers for ${file.name}: " +
                        "$symbolCount symbols exceed the lsp.inheritance.markers.max.symbols limit")
    }
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspFeaturesRefreshing.refreshLineMarkers(lspClient.project, file)
  }

  override fun clearAdditionalCache() {
    synchronized(capLoggedFiles) { capLoggedFiles.clear() }
  }

  private data class SymbolWithParent(val symbol: DocumentSymbol, val parent: DocumentSymbol?)

  private class SymbolScope(
    val interfaceMembers: List<SymbolWithParent>,
    val containers: List<SymbolWithParent>,
    val classMembers: List<SymbolWithParent>,
  )

  private fun flattenWithParent(symbols: List<DocumentSymbol>): List<SymbolWithParent> {
    val result = ArrayList<SymbolWithParent>()
    fun visit(symbol: DocumentSymbol, parent: DocumentSymbol?) {
      result.add(SymbolWithParent(symbol, parent))
      symbol.children?.forEach { visit(it, symbol) }
    }
    symbols.forEach { visit(it, null) }
    return result
  }

  companion object {
    private const val MAX_PARALLEL_REQUESTS = 4
  }
}
