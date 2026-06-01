package com.intellij.platform.lsp.impl.features.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCallHierarchySupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.features.hierarchy.findSupportingServer
import com.intellij.platform.lsp.impl.features.hierarchy.getTargetFromEditor
import com.intellij.psi.PsiElement

private val LSP_CALL_HIERARCHY_SERVER_KEY = Key.create<LspServerImpl>(
  "lsp.call.hierarchy.server"
)

internal class LspCallHierarchyProvider : HierarchyProvider {
  override fun getTarget(dataContext: DataContext): PsiElement? =
    getTargetFromEditor(dataContext, LSP_CALL_HIERARCHY_SERVER_KEY, ::shouldProceedWithServer)

  override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
    // Server is cached in user data by getTarget(), but BaseOnThis* actions bypass getTarget()
    // and call createHierarchyBrowser() directly, so we need to find the supporting server
    val server = target.getUserData(LSP_CALL_HIERARCHY_SERVER_KEY)
                 ?: findSupportingServer(target.project, target.containingFile.virtualFile, ::shouldProceedWithServer)
                 ?: error("Missing cached LSP server on call-hierarchy target")
    return LspCallHierarchyBrowser(target.project, target, server)
  }

  override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
    val browser = hierarchyBrowser as? LspCallHierarchyBrowser ?: return
    browser.changeView(CallHierarchyBrowserBase.getCallerType())
  }

  private fun shouldProceedWithServer(server: LspServerImpl, file: VirtualFile): Boolean {
    val customizer = server.descriptor.lspCustomization.callHierarchyCustomizer
    return customizer is LspCallHierarchySupport &&
           server.supportsCallHierarchy(file) &&
           customizer.shouldAskServerForCallHierarchy(file)
  }
}