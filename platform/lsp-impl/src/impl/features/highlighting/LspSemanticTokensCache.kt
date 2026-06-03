package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspSemanticTokensSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.util.getLsp4jPosition
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.SemanticTokensLegend
import org.eclipse.lsp4j.SemanticTokensParams

/**
 * [Semantic Tokens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_semanticTokens)
 */
internal class LspSemanticTokensCache(private val lspClient: LspClientImpl) : LspHighlightingCache<LspSemanticToken>(lspClient.project) {
  @RequiresReadLock
  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val semanticTokensSupport = lspClient.descriptor.lspCustomization.semanticTokensCustomizer as? LspSemanticTokensSupport ?: return false
    val psiFile = PsiManager.getInstance(lspClient.project).findFile(file) ?: return false
    if (!semanticTokensSupport.shouldAskServerForSemanticTokens(psiFile)) return false

    val supportsFull = lspClient.serverCapabilities?.semanticTokensProvider?.full?.let { it.left ?: true }
    return supportsFull == true
  }

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, LspSemanticToken>>? {
    val psiModTracker = PsiModificationTracker.getInstance(lspClient.project)
    val modCount = psiModTracker.modificationCount
    val legend = lspClient.serverCapabilities?.semanticTokensProvider?.legend ?: return null

    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument, cellRange ->
      val response = lspClient.sendRequest {
        it.textDocumentService.semanticTokensFull(SemanticTokensParams(lspDocument.id))
      } ?: return@forEachDocumentInFile null

      if (psiModTracker.modificationCount != modCount) {
        // The result will be ignored anyway in `LspHighlightingCache.responseReceived`, so let's save time by not calling decodeSemanticTokens()
        return@forEachDocumentInFile emptyList()
      }

      readAction {
        val hostDocument = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
        val document = lspDocument.prepareDocument(hostDocument) { host ->
          val cellStartOffset = getOffsetInDocument(host, cellRange.start) ?: return@prepareDocument null
          val cellEndOffset = getOffsetInDocument(host, cellRange.end) ?: return@prepareDocument null
          val cellText = host.charsSequence.subSequence(cellStartOffset, cellEndOffset)
          DocumentImpl(cellText, true)
        } ?: return@readAction null

        decodeSemanticTokens(document, response.data, legend).map { (range, token) ->
          lspDocument.toHostRange(range) to token
        }
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  @RequiresReadLock
  private fun decodeSemanticTokens(document: Document, data: List<Int>, legend: SemanticTokensLegend): List<Pair<Range, LspSemanticToken>> {
    if (data.size % 5 != 0) {
      thisLogger().warn("Unexpected semantic tokens data length from the server: ${data.size}")
      return emptyList()
    }

    // https://microsoft.github.io/language-server-protocol/specification#textDocument_semanticTokens
    // at index 5*i - deltaLine: token line number, relative to the previous token
    // at index 5*i+1 - deltaStart: token start character, relative to the previous token (relative to 0 or the previous token’s start if they are on the same line)
    // at index 5*i+2 - length: the length of the token.
    // at index 5*i+3 - tokenType: will be looked up in SemanticTokensLegend.tokenTypes. We currently ask that tokenType < 65536.
    // at index 5*i+4 - tokenModifiers: each set bit will be looked up in SemanticTokensLegend.tokenModifiers

    var line = 0
    var column = 0
    val numTokens = data.size / 5

    val result = ArrayList<Pair<Range, LspSemanticToken>>(numTokens)

    for (i in 0 until numTokens) {
      if (i % 100 == 0) ProgressManager.checkCanceled()

      val deltaLine = data[5 * i]
      val deltaStart = data[5 * i + 1]
      val length = data[5 * i + 2]
      val encodedTokenType = data[5 * i + 3]
      val encodedTokenModifiers = data[5 * i + 4]

      line += deltaLine
      column = if (deltaLine == 0) column + deltaStart else deltaStart

      if (encodedTokenType >= legend.tokenTypes.size) {
        thisLogger().warn("Unexpected encodedTokenType: $encodedTokenType, legend.tokenTypes.size = ${legend.tokenTypes.size}")
        continue
      }

      val tokenType = legend.tokenTypes[encodedTokenType]

      val tokenModifiers = mutableListOf<String>()
      for ((index, modifier) in legend.tokenModifiers.withIndex()) {
        if (encodedTokenModifiers and (1 shl index) != 0) {
          tokenModifiers.add(modifier)
        }
      }

      val startPosition = Position(line, column)
      val offset = getOffsetInDocument(document, startPosition) ?: continue
      val endOffset = offset + length
      if (endOffset > document.textLength) {
        continue
      }
      val endPosition = getLsp4jPosition(document, endOffset)

      result.add(Range(startPosition, endPosition) to LspSemanticToken(tokenType, tokenModifiers))
    }

    return result
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(lspClient.project).scheduleHighlightingRefresh(file)
  }
}
