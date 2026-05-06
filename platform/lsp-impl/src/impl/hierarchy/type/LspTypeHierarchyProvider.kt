package com.intellij.platform.lsp.impl.hierarchy.type

import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspTypeHierarchySupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.hierarchy.findSupportingServer
import com.intellij.platform.lsp.impl.hierarchy.getTargetFromEditor
import com.intellij.psi.PsiElement

private val LSP_TYPE_HIERARCHY_SERVER_KEY = Key.create<LspServerImpl>("lsp.type.hierarchy.server")

internal class LspTypeHierarchyProvider : HierarchyProvider {
  override fun getTarget(dataContext: DataContext): PsiElement? =
    getTargetFromEditor(dataContext, LSP_TYPE_HIERARCHY_SERVER_KEY, ::shouldProceedWithServer)

  override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
    // Server is cached in user data by getTarget(), but BaseOnThis* actions bypass getTarget()
    // and call createHierarchyBrowser() directly, so we need to find the supporting server
    val server = target.getUserData(LSP_TYPE_HIERARCHY_SERVER_KEY)
                 ?: findSupportingServer(target.project, target.containingFile.virtualFile, ::shouldProceedWithServer)
                 ?: error("Missing cached LSP server on type-hierarchy target")
    return LspTypeHierarchyBrowser(target.project, target, server)
  }

  override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
    val browser = hierarchyBrowser as? LspTypeHierarchyBrowser ?: return
    browser.changeView(TypeHierarchyBrowserBase.getSubtypesHierarchyType())
  }

  private fun shouldProceedWithServer(server: LspServerImpl, file: VirtualFile): Boolean {
    val customizer = server.descriptor.lspCustomization.typeHierarchyCustomizer
    return customizer is LspTypeHierarchySupport &&
           server.supportsTypeHierarchy(file) &&
           customizer.shouldAskServerForTypeHierarchy(file)
  }
}