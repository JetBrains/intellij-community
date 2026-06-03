package com.intellij.platform.lsp.impl.features.hierarchy.call

import com.intellij.ide.hierarchy.CallHierarchyBrowserBase
import com.intellij.ide.hierarchy.HierarchyBrowser
import com.intellij.ide.hierarchy.HierarchyProvider
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCallHierarchySupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.features.hierarchy.findSupportingClient
import com.intellij.platform.lsp.impl.features.hierarchy.getTargetFromEditor
import com.intellij.psi.PsiElement

private val LSP_CALL_HIERARCHY_CLIENT_KEY = Key.create<LspClientImpl>("lsp.call.hierarchy.client")

internal class LspCallHierarchyProvider : HierarchyProvider {
  override fun getTarget(dataContext: DataContext): PsiElement? =
    getTargetFromEditor(dataContext, LSP_CALL_HIERARCHY_CLIENT_KEY, ::shouldProceedWithClient)

  override fun createHierarchyBrowser(target: PsiElement): HierarchyBrowser {
    // Client is cached in user data by getTarget(), but BaseOnThis* actions bypass getTarget()
    // and call createHierarchyBrowser() directly, so we need to find the supporting client
    val client = target.getUserData(LSP_CALL_HIERARCHY_CLIENT_KEY)
                 ?: findSupportingClient(target.project, target.containingFile.virtualFile, ::shouldProceedWithClient)
                 ?: error("Missing cached LSP client on call-hierarchy target")
    return LspCallHierarchyBrowser(target.project, target, client)
  }

  override fun browserActivated(hierarchyBrowser: HierarchyBrowser) {
    val browser = hierarchyBrowser as? LspCallHierarchyBrowser ?: return
    browser.changeView(CallHierarchyBrowserBase.getCallerType())
  }

  private fun shouldProceedWithClient(client: LspClientImpl, file: VirtualFile): Boolean {
    val customizer = client.descriptor.lspCustomization.callHierarchyCustomizer
    return customizer is LspCallHierarchySupport &&
           client.supportsCallHierarchy(file) &&
           customizer.shouldAskServerForCallHierarchy(file)
  }
}