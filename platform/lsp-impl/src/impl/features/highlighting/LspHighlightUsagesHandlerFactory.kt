package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.api.customization.LspDocumentHighlightsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer
import org.eclipse.lsp4j.DocumentHighlightKind
import org.jetbrains.annotations.Unmodifiable

internal class LspHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactory, DumbAware {
  override fun createHighlightUsagesHandler(editor: Editor, psiFile: PsiFile): HighlightUsagesHandlerBase<PsiElement>? {
    val virtualFile = psiFile.virtualFile ?: return null
    if (virtualFile is VirtualFileWindow || !virtualFile.isInLocalFileSystem) return null
    val clients = LspClientManagerImpl.getInstanceImpl(psiFile.project).getClientsWithThisFileOpen(virtualFile)
      .filter { shouldProceedWithClient(it, psiFile) }

    if (clients.isEmpty()) return null

    return object : HighlightUsagesHandlerBase<PsiElement>(editor, psiFile), DumbAware {
      override fun getTargets(): @Unmodifiable List<PsiElement?> {
        return listOf(psiFile)
      }

      override fun selectTargets(targets: @Unmodifiable List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
        selectionConsumer.consume(targets.toMutableList())
      }

      override fun computeUsages(targets: List<PsiElement?>) {
        val offset = editor.caretModel.offset

        clients
          .flatMap { it.requestExecutor.getDocumentHighlightsCaching(virtualFile, offset) ?: emptyList() }
          .forEach {
            when (it.kind) {
              DocumentHighlightKind.Write -> myWriteUsages.add(it.textRange)
              else -> myReadUsages.add(it.textRange)
            }
          }
      }
    }
  }

  private fun shouldProceedWithClient(client: LspClientImpl, file: PsiFile): Boolean {
    val customizer = client.descriptor.lspCustomization.documentHighlightsCustomizer
    return customizer is LspDocumentHighlightsSupport &&
           client.supportsDocumentHighlights(file.virtualFile) &&
           customizer.shouldAskServerForDocumentHighlights(file)
  }
}