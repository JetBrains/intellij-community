package com.intellij.platform.lsp.impl.features.documentSymbol

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TextEditorBasedStructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ColoredItemPresentation
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.features.navigation.navigateToLspPosition
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolTag
import javax.swing.Icon

internal class LspStructureViewFactory : PsiStructureViewFactory {
  override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
    psiFile.virtualFile?.let { (it as? VirtualFileWindow)?.delegate ?: it }
    val file = psiFile.virtualFile
    if (file == null || file is VirtualFileWindow) return null

    val lspServer = LspServerManagerImpl.getInstanceImpl(psiFile.project).getServersWithThisFileOpen(file).firstOrNull {
      it.descriptor.lspCustomization.documentSymbolCustomizer.structureViewSupport &&
      it.supportsDocumentSymbol(file)
    } ?: return null

    return object : TreeBasedStructureViewBuilder() {
      override fun createStructureViewModel(editor: Editor?): StructureViewModel {
        return LspStructureViewModel(lspServer, psiFile, editor)
      }

      override fun isRootNodeShown(): Boolean = false
    }
  }
}

internal class LspStructureViewModel(val lspServer: LspServerImpl, psiFile: PsiFile, editor: Editor?) :
  TextEditorBasedStructureViewModel(editor, psiFile), StructureViewModel.ElementInfoProvider {
  private lateinit var root: LspStructureViewRoot
  private var lastPsiModificationCount: Long = -1

  init {
    val psiModCount = psiFile.manager.modificationTracker.modificationCount
    lastPsiModificationCount = psiModCount
    initRoot()
  }

  override fun getRoot(): StructureViewTreeElement = root

  override fun isValid(): Boolean {
    return psiFile.manager.modificationTracker.modificationCount == lastPsiModificationCount
           && super.isValid()
  }

  fun initRoot() {
    val file = psiFile.virtualFile
    val documentSymbols = lspServer.requestExecutor.getDocumentSymbolsCaching(file) ?: emptyList()
    root = LspStructureViewRoot(lspServer, psiFile, documentSymbols)
  }

  override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean {
    return false
  }

  override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean {
    return false
  }

  override fun getCurrentEditorElement(): DocumentSymbol? {
    if (editor == null) return null

    val file = psiFile
    if (file == null || !file.isValid()) return null

    val offset = editor.getCaretModel().offset

    // has to equal StructureViewTreeElement.getValue
    return findSymbolsAroundOffset(file.fileDocument, root.children, offset)?.value
  }

  /**
   * Compare with [com.intellij.platform.lsp.impl.features.documentSymbol.LspFileBreadcrumbsCollector.findSymbolsAroundOffset]
   */
  private fun findSymbolsAroundOffset(
    document: Document,
    documentSymbols: Array<LspDocumentSymbolTreeElement>,
    offset: Int,
  ): LspDocumentSymbolTreeElement? {
    var currentLevelSymbols: Array<LspDocumentSymbolTreeElement>? = documentSymbols
    var relevantSymbol: LspDocumentSymbolTreeElement? = null

    while (currentLevelSymbols != null) {
      val child = currentLevelSymbols.find { documentSymbol ->
        val startOffset = getOffsetInDocument(document, documentSymbol.value.range.start)
        val endOffset = getOffsetInDocument(document, documentSymbol.value.range.end)
        startOffset != null && offset >= startOffset && endOffset != null && offset <= endOffset
      }
      if (child != null) {
        relevantSymbol = child
        currentLevelSymbols = child.children
      }
      else {
        currentLevelSymbols = null
      }
    }
    return relevantSymbol
  }

}

private class LspStructureViewRoot(
  private val lspServer: LspServerImpl,
  private val psiFile: PsiFile,
  private val documentSymbols: List<DocumentSymbol>,
) : StructureViewTreeElement {
  override fun getValue(): PsiFile {
    return psiFile
  }

  override fun getChildren(): Array<LspDocumentSymbolTreeElement> {
    return documentSymbols.map { LspDocumentSymbolTreeElement(lspServer, psiFile, it) }.toTypedArray()
  }

  override fun getPresentation(): ItemPresentation {
    return psiFile.presentation ?: object : ItemPresentation {
      override fun getPresentableText(): @NlsSafe String {
        return psiFile.name
      }

      override fun getIcon(unused: Boolean): Icon? {
        return null
      }
    }
  }
}

internal class LspDocumentSymbolTreeElement(
  private val lspServer: LspServerImpl,
  private val psiFile: PsiFile,
  private val symbol: DocumentSymbol,
) : StructureViewTreeElement {
  override fun getValue(): DocumentSymbol {
    return symbol
  }

  override fun getChildren(): Array<LspDocumentSymbolTreeElement> {
    return symbol.children?.map { LspDocumentSymbolTreeElement(lspServer, psiFile, it) }?.toTypedArray() ?: emptyArray()
  }

  override fun getPresentation(): ItemPresentation = object : ColoredItemPresentation {
    override fun getPresentableText(): @NlsSafe String? {
      return symbol.name
    }

    override fun getLocationString(): @NlsSafe String? {
      return symbol.detail
    }

    override fun getIcon(unused: Boolean): Icon? {
      val symbolKindCustomizer = lspServer.descriptor.lspCustomization.symbolKindCustomizer
      return symbolKindCustomizer.getIcon(symbol.kind)
    }

    override fun getTextAttributesKey(): TextAttributesKey? {
      @Suppress("DEPRECATION")
      val deprecated = symbol.tags?.contains(SymbolTag.Deprecated) ?: symbol.deprecated ?: false
      return if (deprecated) CodeInsightColors.DEPRECATED_ATTRIBUTES else null
    }
  }

  override fun canNavigate(): Boolean = canNavigateToSource()
  override fun canNavigateToSource(): Boolean = true

  override fun navigate(requestFocus: Boolean) {
    navigateToLspPosition(psiFile.virtualFile, psiFile.project, symbol.selectionRange.start, requestFocus)
  }
}