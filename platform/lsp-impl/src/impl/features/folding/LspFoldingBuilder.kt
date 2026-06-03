package com.intellij.platform.lsp.impl.features.folding

import com.intellij.codeInsight.folding.CodeFoldingSettings
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.impl.LspClientManagerImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.eclipse.lsp4j.FoldingRangeKind


internal class LspFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
    // LspFoldingBuilder.buildFoldRegions can be called as part of nested CompositeFoldingBuilder
    // With LSP, we're only interested in top-level real files
    val psiFile = root as? PsiFile ?: return emptyArray()

    val file = psiFile.virtualFile ?: return emptyArray()
    if (file is VirtualFileWindow || !file.isInLocalFileSystem) return emptyArray()

    val foldingRangeInfos = LspClientManagerImpl.getInstanceImpl(psiFile.project)
      .getClientsWithThisFileOpen(file)
      .flatMap { it.getFoldingRangeInfos(file) }

    if (foldingRangeInfos.isEmpty()) {
      return emptyArray()
    }

    val settings = CodeFoldingSettings.getInstance()
    val node = psiFile.node
    return foldingRangeInfos.mapNotNull { info ->
      if (info.textRange.isEmpty) {
        return@mapNotNull null
      }

      val collapsedByDefault = when (info.highlightingInfo.kind) {
        FoldingRangeKind.Imports -> settings.COLLAPSE_IMPORTS
        FoldingRangeKind.Region -> settings.COLLAPSE_CUSTOM_FOLDING_REGIONS
        FoldingRangeKind.Comment -> null // There's LSP/IJ semantic mismatch: we have `settings.COLLAPSE_DOC_COMMENTS`
        else -> null
      }

      LspFoldingDescriptor(
        node,
        info.textRange, // always range in file
        info.highlightingInfo.collapsedText,
        collapsedByDefault,
      )
    }.toTypedArray()
  }

  override fun isCollapsedByDefault(node: ASTNode): Boolean = false

  /**
   * May be called by [com.intellij.lang.folding.CompositeFoldingBuilder.FoldingDescriptorWrapper.calcPlaceholderText]
   * because `null` value is ambiguous, so we just return `null` again
   */
  override fun getPlaceholderText(node: ASTNode, range: TextRange): String? = null

  override fun getPlaceholderText(node: ASTNode): String? = throw UnsupportedOperationException()
}

private class LspFoldingDescriptor(
  node: ASTNode,
  textRange: TextRange,
  private val collapsedText: String?,
  collapsedByDefault: Boolean?,
) : FoldingDescriptor(
  node,
  textRange,
  null,
  collapsedText,
  collapsedByDefault,
  emptySet()
) {
  /**
   * Override avoids calling [com.intellij.lang.folding.CompositeFoldingBuilder.getPlaceholderText]
   */
  override fun getPlaceholderText(): String? {
    return collapsedText
  }

  override fun setPlaceholderText(placeholderText: String?) {}
}