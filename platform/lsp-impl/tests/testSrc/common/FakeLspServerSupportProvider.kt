package com.intellij.platform.lsp.common

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ServerCapabilities

internal fun TestFixture<Project>.fakeLspServerProviderFixture(
  lspCustomization: LspCustomization = LspCustomization(),
  configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
): TestFixture<FakeLspServerHandle> = testFixture { _ ->
  val projectFixture = this@fakeLspServerProviderFixture
  val project = projectFixture.init()

  extensionPointFixture(LspServerSupportProvider.EP_NAME) {
    FakeLspServerSupportProvider()
  }.init()

  project.putUserData(FAKE_LSP_CUSTOMIZATION_KEY, lspCustomization)
  project.putUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY, configureClientCapabilities)
  project.putUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY, configureServerCapabilities)
  project.putUserData(FAKE_LSP_CREATE_CLIENT_KEY, createLsp4jClient)

  initialized(FakeLspServerHandle()) {
    project.putUserData(FAKE_LSP_CUSTOMIZATION_KEY, null)
    project.putUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY, null)
    project.putUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY, null)
    project.putUserData(FAKE_LSP_CREATE_CLIENT_KEY, null)
  }
}

internal class FakeLspServerHandle {
  // todo move fun configureServerSession here
}

internal val FAKE_LSP_CUSTOMIZATION_KEY = Key.create<LspCustomization>("FAKE_LSP_CUSTOMIZATION_KEY")
internal val FAKE_LSP_SERVER_CAPABILITIES_KEY = Key.create<ServerCapabilities.() -> Unit>("FAKE_LSP_SERVER_CAPABILITIES_KEY")
internal val FAKE_LSP_CLIENT_CAPABILITIES_KEY = Key.create<ClientCapabilities.() -> Unit>("FAKE_LSP_CLIENT_CAPABILITIES_KEY")
internal val FAKE_LSP_CREATE_CLIENT_KEY = Key.create<(LspServerNotificationsHandler) -> Lsp4jClient>("FAKE_LSP_CREATE_CLIENT_KEY")

internal class FakeLspServerSupportProvider : LspServerSupportProvider {
  override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
    val customization = project.getUserData(FAKE_LSP_CUSTOMIZATION_KEY) ?: LspCustomization()
    val configureServerCapabilities = project.getUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY)
    val configureClientCapabilities = project.getUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY)
    val createLsp4jClient = project.getUserData(FAKE_LSP_CREATE_CLIENT_KEY)
    serverStarter.ensureServerStarted(
      FakeLspServerDescriptor(project, customization, configureServerCapabilities, configureClientCapabilities, createLsp4jClient))
  }
}

internal class FakeLspServerDescriptor(
  project: Project,
  override val lspCustomization: LspCustomization,
  private val configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  private val configureClientCapabilities: (ClientCapabilities.() -> Unit)?,
  private val configureLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
) : ProjectWideLspServerDescriptor(project, "FakeLspServer") {
  lateinit var server: FakeLspServer

  override fun isSupportedFile(file: VirtualFile) = true

  override val clientCapabilities: ClientCapabilities
    get() = super.clientCapabilities.apply {
      configureClientCapabilities?.invoke(this)
    }

  override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
    configureLsp4jClient?.invoke(handler) ?: super.createLsp4jClient(handler)

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