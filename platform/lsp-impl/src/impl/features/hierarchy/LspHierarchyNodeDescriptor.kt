package com.intellij.platform.lsp.impl.features.hierarchy

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.roots.ui.util.CompositeAppearance
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.navigation.navigateToLspPosition
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import javax.swing.Icon

internal class LspHierarchyNodeDescriptor(
  internal val client: LspClientImpl,
  internal val item: Lsp4jHierarchyItem?,
  element: PsiElement,
  parent: HierarchyNodeDescriptor?,
  internal val isBase: Boolean,
) : HierarchyNodeDescriptor(client.project, parent, element, isBase), Navigatable {

  override fun update(): Boolean {
    super.update()
    if (item == null) return false
    val oldText = myHighlightedText
    myHighlightedText = CompositeAppearance()

    myHighlightedText.ending.addText(item.name)
    val detail = item.detail
    if (!detail.isNullOrBlank()) {
      myHighlightedText.ending.addText(" : $detail", getPackageNameAttributes())
    }
    myName = myHighlightedText.text
    return myHighlightedText != oldText
  }

  override fun canNavigate(): Boolean {
    if (item == null) return false
    return item.uri.isNotEmpty()
  }

  override fun navigate(requestFocus: Boolean) {
    if (item == null) return
    val targetFile = client.descriptor.findFileByUri(item.uri) ?: return
    navigateToLspPosition(targetFile, project, item.selectionRange.start, requestFocus)
  }

  override fun getIcon(element: PsiElement): Icon? {
    if (item == null) return null
    val kind = item.kind
    return client.descriptor.lspCustomization.symbolKindCustomizer.getIcon(kind)
  }
}
