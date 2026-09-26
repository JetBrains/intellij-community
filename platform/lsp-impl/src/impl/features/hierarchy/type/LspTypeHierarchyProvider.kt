package com.intellij.platform.lsp.impl.features.hierarchy.type

import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.hierarchy.findSupportingClient
import com.intellij.platform.lsp.impl.features.hierarchy.getTargetFromEditor
import com.intellij.psi.PsiElement

private val LSP_TYPE_HIERARCHY_CLIENT_KEY = Key.create<LspClientImpl>("lsp.type.hierarchy.client")

internal class LspTypeHierarchyProvider : HierarchyProvider {
  override fun getTarget(dataContext: DataContext): PsiElement? =
    getTargetFromEditor(dataContext, LSP_TYPE_HIERARCHY_CLIENT_KEY, ::shouldProceedWithClient)

  override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
    // Client is cached in user data by getTarget(), but BaseOnThis* actions bypass getTarget()
    // and call createHierarchyBrowser() directly, so we need to find the supporting client
    val client = target.getUserData(LSP_TYPE_HIERARCHY_CLIENT_KEY)
                 ?: findSupportingClient(target.project, target.containingFile.virtualFile, ::shouldProceedWithClient)
                 ?: error("Missing cached LSP client on type-hierarchy target")
    return LspTypeHierarchyBrowser(target.project, target, client)
  }

  override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
    val browser = hierarchyBrowser as? LspTypeHierarchyBrowser ?: return
    browser.changeView(TypeHierarchyBrowserBase.getSubtypesHierarchyType())
  }

  private fun shouldProceedWithClient(client: LspClientImpl, file: VirtualFile): Boolean {
    val customizer = client.descriptor.lspCustomization.typeHierarchyCustomizer
    return customizer is LspTypeHierarchySupport &&
           client.supportsTypeHierarchy(file) &&
           customizer.shouldAskServerForTypeHierarchy(file)
  }
}