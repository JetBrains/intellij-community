// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.workspaceSymbol

import com.intellij.navigation.ChooseByNameContributorEx2
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspWorkspaceSymbolSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.indexing.IdFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.WorkspaceSymbol

internal abstract class LspWorkspaceSymbolContributor : ChooseByNameContributorEx2, DumbAware {

  override fun processNames(processor: Processor<in String>, parameters: FindSymbolParameters) {
    val project = parameters.project
    val serverManager = LspServerManagerImpl.getInstanceImpl(project)
    val query = parameters.completePattern
    // skipping the processor.process early helps to reduce complexity from the platform side
    if (query.isBlank() || serverManager.getAllRunningServers().isEmpty()) return

    processor.process(query)
  }

  override fun processNames(
    processor: Processor<in String>,
    scope: GlobalSearchScope,
    filter: IdFilter?,
  ) {
    // ignore as it should not be called,
    // because LspWorkspaceSymbolContributor.processNames(Processor<in String>, FindSymbolParameters) is called instead
  }

  override fun processElementsWithName(
    name: String,
    processor: Processor<in NavigationItem>,
    parameters: FindSymbolParameters,
  ) {
    val project = parameters.project
    val scope = parameters.searchScope
    val serverManager = LspServerManagerImpl.getInstanceImpl(project)
    val servers = serverManager.getAllRunningServers().filter {
      it.descriptor.lspCustomization.workspaceSymbolCustomizer is LspWorkspaceSymbolSupport && it.supportsGoToSymbol()
    }

    if (servers.isEmpty()) return

    runBlockingCancellable {
      servers.map { server ->
        async {
          getWorkspaceSymbols(server, scope, name)
            .forEach { processor.process(it) }
        }
      }.awaitAll()
    }
  }

  protected abstract fun shouldAcceptSymbolKind(symbolKind: SymbolKind): Boolean

  @RequiresBackgroundThread
  private fun getWorkspaceSymbols(server: LspServerImpl, scope: GlobalSearchScope, query: String = ""): List<NavigationItem> {
    val result = server.requestExecutor.getWorkspaceSymbolsCaching(query)
                 ?: return emptyList()

    return result.mapNotNull { createNavigationItemFromWorkspaceSymbol(server, scope, it) }
  }

  private fun createNavigationItemFromWorkspaceSymbol(
    client: LspClient,
    scope: GlobalSearchScope,
    symbol: WorkspaceSymbol,
  ): NavigationItem? {
    val uri = symbol.location.left?.uri ?: symbol.location.right?.uri ?: return null
    val virtualFile = client.descriptor.findFileByUri(uri) ?: return null
    if (!scope.contains(virtualFile) || !shouldAcceptSymbolKind(symbol.kind)) return null
    val customizer = client.descriptor.lspCustomization.workspaceSymbolCustomizer as LspWorkspaceSymbolSupport
    return customizer.createNavigationItem(client, symbol)
  }
}