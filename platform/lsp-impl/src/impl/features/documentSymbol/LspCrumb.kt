package com.intellij.platform.lsp.impl.features.documentSymbol

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.platform.lsp.api.customization.LspSymbolKindCustomizer
import com.intellij.platform.lsp.util.getOffsetInDocument
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.ui.components.breadcrumbs.Crumb
import com.intellij.xml.breadcrumbs.NavigatableCrumb
import org.eclipse.lsp4j.DocumentSymbol

internal class LspCrumb(
  val project: Project,
  customizer: LspSymbolKindCustomizer,
  private val document: Document,
  private val symbol: DocumentSymbol,
) : Crumb.Impl(customizer.getIcon(symbol.kind), symbol.name, null), NavigatableCrumb {
  override fun getHighlightRange(): TextRange? {
    /**
     * `getHighlightRange` seems to be a misnomer, `PsiCrumb` uses [com.intellij.psi.PsiElement.getTextRange]
     */
    return getRangeInDocument(document, symbol.range)
  }

  override fun getAnchorOffset(): Int {
    return getOffsetInDocument(document, symbol.selectionRange.start) ?: -1
  }

  /**
   * @see com.intellij.xml.breadcrumbs.PsiCrumb.navigate
   */
  override fun navigate(editor: Editor, withSelection: Boolean) {
    val offset = anchorOffset
    if (offset != -1) {
      // better not to use OpenFileDescriptor, it skips scroll animations, and with breadcrumb, the file is already opened
      moveEditorCaretTo(editor, offset)
    }

    if (withSelection) {
      val range = highlightRange
      select(editor, range)
    }
  }
}
