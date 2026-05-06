package com.intellij.platform.lsp.common

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ServerCapabilities

internal val FAKE_LSP_CUSTOMIZATION_KEY = Key.create<LspCustomization>("FAKE_LSP_CUSTOMIZATION_KEY")
internal val FAKE_LSP_SERVER_CAPABILITIES_KEY = Key.create<ServerCapabilities.() -> Unit>("FAKE_LSP_SERVER_CAPABILITIES_KEY")
internal val FAKE_LSP_CLIENT_CAPABILITIES_KEY = Key.create<ClientCapabilities.() -> Unit>("FAKE_LSP_CLIENT_CAPABILITIES_KEY")

internal class FakeLspServerSupportProvider : LspServerSupportProvider {
  override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
    val customization = project.getUserData(FAKE_LSP_CUSTOMIZATION_KEY) ?: LspCustomization()
    val configureServerCapabilities = project.getUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY)
    val configureClientCapabilities = project.getUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY)
    serverStarter.ensureServerStarted(FakeLspServerDescriptor(project, customization, configureServerCapabilities, configureClientCapabilities))
  }
}

internal class FakeLspServerDescriptor(
  project: Project,
  override val lspCustomization: LspCustomization,
  private val configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  private val configureClientCapabilities: (ClientCapabilities.() -> Unit)?,
) : ProjectWideLspServerDescriptor(project, "FakeLspServer") {
  lateinit var server: FakeLspServer

  override fun isSupportedFile(file: VirtualFile) = true

  override val clientCapabilities: ClientCapabilities
    get() = super.clientCapabilities.apply {
      configureClientCapabilities?.invoke(this)
    }

  override fun createCommandLine(): GeneralCommandLine {
    /** command is usable for debugging **/
    return object : GeneralCommandLine("fake --lsp") {
      override fun startProcess(): Process {
        val fakeServer = FakeLspServer(configureServerCapabilities)
        server = fakeServer
        return fakeServer
      }
    }
  }
}