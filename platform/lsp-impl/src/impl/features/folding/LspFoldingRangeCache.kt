package com.intellij.platform.lsp.impl.features.folding

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFoldingRangeSupport
import com.intellij.platform.lsp.impl.LspClientImpl
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
internal class LspFoldingRangeCache(private val lspClient: LspClientImpl) : LspHighlightingCache<FoldingRange>(lspClient.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean =
    lspClient.descriptor.lspCustomization.foldingRangeCustomizer is LspFoldingRangeSupport
    && lspClient.supportsFoldingRange(file)

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, FoldingRange>>? {
    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = FoldingRangeRequestParams(lspDocument.id)
      lspClient.sendRequest { it.textDocumentService.foldingRange(params) }?.map { foldingRange ->
        val mapped = lspDocument.mapFoldingRange(foldingRange)
        foldingRangeToLsp4jRange(mapped) to mapped
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    val project = lspClient.project
    val foldingManager = CodeFoldingManager.getInstance(project)
    readAction {
      // scheduleAsyncFoldingUpdate restarts daemon highlighting synchronously; daemon cancellation listeners
      // are documented to run under read action and may read PSI/editor context.
      for (editor in EditorFactory.getInstance().allEditors) {
        if (editor.project == project && editor.virtualFile == file) {
          foldingManager.scheduleAsyncFoldingUpdate(editor)
        }
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
