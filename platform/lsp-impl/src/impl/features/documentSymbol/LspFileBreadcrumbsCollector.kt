// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.documentSymbol

import com.intellij.codeInsight.breadcrumbs.FileBreadcrumbsCollector
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.ui.components.breadcrumbs.StickyLineInfo
import com.intellij.xml.breadcrumbs.PsiFileBreadcrumbsCollector
import org.eclipse.lsp4j.DocumentSymbol

internal class LspFileBreadcrumbsCollector(private val project: Project) : FileBreadcrumbsCollector() {
  override fun requiresProvider(): Boolean = false

  override fun handlesFile(file: VirtualFile): Boolean {
    val lspClient = getLspClient(file)

    return lspClient != null
  }

  override fun watchForChanges(file: VirtualFile, editor: Editor, disposable: Disposable, changesHandler: Runnable) {
    PsiFileBreadcrumbsCollector.watchForChanges(project, file, disposable, changesHandler)
  }

  override fun computeCrumbs(file: VirtualFile, document: Document, offset: Int, forcedShown: Boolean?): Iterable<Crumb> {
    val lspClient = getLspClient(file) ?: return emptyList()
    val documentSymbols = lspClient.requestExecutor.getDocumentSymbolsCaching(file) ?: return emptyList()

    val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
    val relevantSymbols = findSymbolsAroundOffset(document, documentSymbols, offset)

    val symbolKindCustomizer = lspClient.descriptor.lspCustomization.symbolKindCustomizer
    return relevantSymbols.map { symbol ->
      LspCrumb(project, symbolKindCustomizer, document, symbol)
    }
  }

  override fun computeStickyLineInfos(file: VirtualFile, document: Document, offset: Int): List<StickyLineInfo> {
    val lspClient = getLspClient(file) ?: return emptyList()
    val documentSymbols = lspClient.requestExecutor.getDocumentSymbolsCaching(file) ?: return emptyList()


    val document = FileDocumentManager.getInstance().getDocument(file) ?: return emptyList()
    val relevantSymbols = findSymbolsAroundOffset(document, documentSymbols, offset)

    return relevantSymbols.mapNotNull { symbol ->
      /**
       * See [com.intellij.openapi.editor.impl.stickyLines.StickyLine.navigateOffset]
       */
      val textOffset = getOffsetInDocument(document, symbol.selectionRange.start)
                       ?: getOffsetInDocument(document, symbol.range.start)
                       ?: return@mapNotNull null
      val endOffset = getOffsetInDocument(document, symbol.range.end)
                      ?: return@mapNotNull null
      StickyLineInfo(textOffset, endOffset, null)
    }
  }

  private fun getLspClient(file: VirtualFile): LspClientImpl? {
    return LspClientManagerImpl.getInstanceImpl(project).getClientsWithThisFileOpen(file).firstOrNull {
      it.descriptor.lspCustomization.documentSymbolCustomizer.breadcrumbsSupport &&
      it.supportsDocumentSymbol(file)
    }
  }

  private fun findSymbolsAroundOffset(document: Document, documentSymbols: List<DocumentSymbol>, offset: Int): List<DocumentSymbol> {
    var currentLevelSymbols: List<DocumentSymbol>? = documentSymbols
    val relevantSymbols = mutableListOf<DocumentSymbol>()

    while (currentLevelSymbols != null) {
      val child = currentLevelSymbols.find { documentSymbol ->
        val startOffset = getOffsetInDocument(document, documentSymbol.range.start)
        val endOffset = getOffsetInDocument(document, documentSymbol.range.end)
        startOffset != null && offset >= startOffset && endOffset != null && offset <= endOffset
      }
      if (child != null) {
        relevantSymbols.add(child)
        currentLevelSymbols = child.children
      }
      else {
        currentLevelSymbols = null
      }
    }
    return relevantSymbols
  }
}
