package com.intellij.platform.lsp.impl.serviceView

import com.intellij.execution.services.ServiceViewContributor
import com.intellij.execution.services.ServiceViewDescriptor
import com.intellij.execution.services.ServiceViewNonActivatingDescriptor
import com.intellij.execution.services.SimpleServiceViewDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspBundle
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.impl.LspClientManagerImpl

internal class LspServiceViewContributor : ServiceViewContributor<LspClient> {
  override fun getViewDescriptor(project: Project): ServiceViewDescriptor = LspRootDescriptor()

  override fun getServices(project: Project): List<LspClient> =
    LspClientManagerImpl.getInstanceImpl(project).getAllClients()

  override fun getServiceDescriptor(project: Project, service: LspClient): ServiceViewDescriptor =
    LspClientServiceViewDescriptor(project, service)
}

private class LspRootDescriptor :
  SimpleServiceViewDescriptor(LspBundle.message("services.lsp.root.node"), AllIcons.Webreferences.Server, "LSP"),
  ServiceViewNonActivatingDescriptor
