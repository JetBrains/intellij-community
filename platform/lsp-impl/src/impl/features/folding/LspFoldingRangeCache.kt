package com.intellij.platform.lsp.impl.features.folding

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFoldingRangeSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.mapFoldingRange
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * [textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specification/#textDocument_foldingRange)
 */
internal class LspFoldingRangeCache(private val lspServer: LspServerImpl) : LspHighlightingCache<FoldingRange>(lspServer.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean =
    lspServer.descriptor.lspCustomization.foldingRangeCustomizer is LspFoldingRangeSupport
    && lspServer.supportsFoldingRange(file)

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, FoldingRange>>? {
    val perDocument = lspServer.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = FoldingRangeRequestParams(lspDocument.id)
      lspServer.sendRequest { it.textDocumentService.foldingRange(params) }?.map { foldingRange ->
        val mapped = lspDocument.mapFoldingRange(foldingRange)
        foldingRangeToLsp4jRange(mapped) to mapped
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    val fileEditorManager = lspServer.project.serviceAsync<FileEditorManager>()
    for (fileEditor in fileEditorManager.getEditors(file)) {
      if (fileEditor is TextEditor) {
        CodeFoldingManager.getInstance(lspServer.project).scheduleAsyncFoldingUpdate(fileEditor.editor)
      }
    }
  }

  private fun foldingRangeToLsp4jRange(foldingRange: FoldingRange): Range {
    val startCharacter = foldingRange.startCharacter ?: Int.MAX_VALUE
    val endCharacter = foldingRange.endCharacter ?: Int.MAX_VALUE

    val startPosition = Position(foldingRange.startLine, startCharacter)
    val endPosition = Position(foldingRange.endLine, endCharacter)

    return Range(startPosition, endPosition)
  }
}
