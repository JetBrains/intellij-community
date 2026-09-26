// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.structureView

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
import com.intellij.platform.lsp.impl.features.documentSymbol.LspStructureViewSupport
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolTag
import javax.swing.Icon

internal class LspStructureViewFactory : PsiStructureViewFactory {
  override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
    val file = psiFile.virtualFile
    if (file == null || file is VirtualFileWindow) return null

    val support = LspStructureViewSupport.find(psiFile.project, file) ?: return null

    return object : TreeBasedStructureViewBuilder() {
      override fun createStructureViewModel(editor: Editor?): StructureViewModel {
        return LspStructureViewModel(support, psiFile, editor)
      }

      override fun isRootNodeShown(): Boolean = false
    }
  }
}

private class LspStructureViewModel(
  private val support: LspStructureViewSupport,
  psiFile: PsiFile,
  editor: Editor?,
) : TextEditorBasedStructureViewModel(editor, psiFile), StructureViewModel.ElementInfoProvider {
  private lateinit var root: LspStructureViewRoot
  private val lastPsiModificationCount: Long = psiFile.manager.modificationTracker.modificationCount

  init {
    initRoot()
  }

  override fun getRoot(): StructureViewTreeElement = root

  override fun isValid(): Boolean = psiFile.manager.modificationTracker.modificationCount == lastPsiModificationCount && super.isValid()

  private fun initRoot() {
    root = LspStructureViewRoot(support, psiFile, support.getDocumentSymbols())
  }

  override fun isAlwaysShowsPlus(element: StructureViewTreeElement?): Boolean = false

  override fun isAlwaysLeaf(element: StructureViewTreeElement?): Boolean = false

  override fun getCurrentEditorElement(): DocumentSymbol? {
    val editor = editor ?: return null
    val psiFile = psiFile.takeIf { it.isValid } ?: return null
    return findSymbolsAroundOffset(psiFile.fileDocument, root.children, editor.caretModel.offset)?.value
  }

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
  private val support: LspStructureViewSupport,
  private val psiFile: PsiFile,
  private val documentSymbols: List<DocumentSymbol>,
) : StructureViewTreeElement {
  override fun getValue(): PsiFile = psiFile

  override fun getChildren(): Array<LspDocumentSymbolTreeElement> {
    return documentSymbols.map { LspDocumentSymbolTreeElement(support, it) }.toTypedArray()
  }

  override fun getPresentation(): ItemPresentation {
    return psiFile.presentation ?: object : ItemPresentation {
      override fun getPresentableText(): @NlsSafe String = psiFile.name

      override fun getIcon(unused: Boolean): Icon? = null
    }
  }
}

private class LspDocumentSymbolTreeElement(
  private val support: LspStructureViewSupport,
  private val symbol: DocumentSymbol,
) : StructureViewTreeElement {
  override fun getValue(): DocumentSymbol = symbol

  override fun getChildren(): Array<LspDocumentSymbolTreeElement> {
    return symbol.children?.map { LspDocumentSymbolTreeElement(support, it) }?.toTypedArray() ?: emptyArray()
  }

  override fun getPresentation(): ItemPresentation = object : ColoredItemPresentation {
    override fun getPresentableText(): @NlsSafe String? = symbol.name

    override fun getLocationString(): @NlsSafe String? = symbol.detail

    override fun getIcon(unused: Boolean): Icon? = support.getIcon(symbol)

    override fun getTextAttributesKey(): TextAttributesKey? {
      @Suppress("DEPRECATION")
      val deprecated = symbol.tags?.contains(SymbolTag.Deprecated) ?: symbol.deprecated ?: false
      return if (deprecated) CodeInsightColors.DEPRECATED_ATTRIBUTES else null
    }
  }

  override fun canNavigate(): Boolean = canNavigateToSource()

  override fun canNavigateToSource(): Boolean = true

  override fun navigate(requestFocus: Boolean) {
    support.navigate(symbol.selectionRange.start, requestFocus)
  }
}
