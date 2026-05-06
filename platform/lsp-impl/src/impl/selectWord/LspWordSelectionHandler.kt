package com.intellij.platform.lsp.impl.selectWord

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandler
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspSelectionRangeSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.SelectionRange

internal class LspWordSelectionHandler : ExtendWordSelectionHandler {
  override fun canSelect(e: PsiElement): Boolean {
    // See `com.intellij.codeInsight.editorActions.SelectWordUtil.processElement`
    // The IntelliJ Platform collects all `ExtendWordSelectionHandlers` that return `true` from `canSelect()`
    // and then calls `select()` for all of them.
    // So, not to duplicate the first 6 lines of the `select()` implementation, it's safe to return `true` from here
    // and then return `null` from `select()` if there is no LSP server-based info for this feature.
    return true
  }

  @RequiresReadLock
  @RequiresBackgroundThread
  override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
    val psiFile: PsiFile = e.containingFile ?: return null
    val file = psiFile.virtualFile ?: return null
    if (file is VirtualFileWindow || !file.isInLocalFileSystem) return null

    val lspServers = LspServerManagerImpl.getInstanceImpl(psiFile.project)
      .getServersWithThisFileOpen(file)
      .filter { shouldProceedWithServer(it, file) }
    if (lspServers.isEmpty()) return null

    val document = editor.document

    return lspServers.flatMap { lspServer ->
      lspServer.requestExecutor.getSelectionRangeCaching(file, cursorOffset)?.let {
        extractTextRanges(document, it)
      }
      ?: emptyList()
    }
  }

  private fun shouldProceedWithServer(lspServer: LspServerImpl, file: VirtualFile): Boolean {
    val customizer = lspServer.descriptor.lspCustomization.selectionRangeCustomizer
    return customizer is LspSelectionRangeSupport &&
           lspServer.supportsSelectionRange(file) &&
           customizer.shouldAskServerForSelectionRange(file)
  }

  private fun extractTextRanges(document: Document, selectionRange: SelectionRange): List<TextRange> {
    val result = ArrayList<TextRange>()
    var current: SelectionRange? = selectionRange
    while (current != null) {
      val textRange = getRangeInDocument(document, current.range)
      if (textRange != null && (result.isEmpty() || result.last() != textRange)) {
        result.add(textRange)
      }
      current = current.parent
    }
    return result
  }
}
