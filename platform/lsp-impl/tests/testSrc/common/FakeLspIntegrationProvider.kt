package com.intellij.platform.lsp.common

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ServerCapabilities

internal fun TestFixture<Project>.fakeLspIntegrationFixture(
  lspCustomization: LspCustomization = LspCustomization(),
  configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  isSupportedFile: ((VirtualFile) -> Boolean)? = null,
): TestFixture<FakeLspIntegration> = testFixture { _ ->
  val projectFixture = this@fakeLspIntegrationFixture
  val project = projectFixture.init()

  extensionPointFixture(LspIntegrationProvider.EP_NAME) {
    FakeLspIntegrationProvider()
  }.init()

  project.putUserData(FAKE_LSP_CUSTOMIZATION_KEY, lspCustomization)
  project.putUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY, configureClientCapabilities)
  project.putUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY, configureServerCapabilities)
  project.putUserData(FAKE_LSP_CREATE_CLIENT_KEY, createLsp4jClient)
  project.putUserData(FAKE_LSP_IS_SUPPORTED_FILE_KEY, isSupportedFile)

  initialized(FakeLspIntegration()) {
    project.putUserData(FAKE_LSP_CUSTOMIZATION_KEY, null)
    project.putUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY, null)
    project.putUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY, null)
    project.putUserData(FAKE_LSP_CREATE_CLIENT_KEY, null)
    project.putUserData(FAKE_LSP_IS_SUPPORTED_FILE_KEY, null)
  }
}

internal class FakeLspIntegration {
  // todo move fun configureServerSession here
}

internal val FAKE_LSP_CUSTOMIZATION_KEY = Key.create<LspCustomization>("FAKE_LSP_CUSTOMIZATION_KEY")
internal val FAKE_LSP_SERVER_CAPABILITIES_KEY = Key.create<ServerCapabilities.() -> Unit>("FAKE_LSP_SERVER_CAPABILITIES_KEY")
internal val FAKE_LSP_CLIENT_CAPABILITIES_KEY = Key.create<ClientCapabilities.() -> Unit>("FAKE_LSP_CLIENT_CAPABILITIES_KEY")
internal val FAKE_LSP_CREATE_CLIENT_KEY = Key.create<(LspServerNotificationsHandler) -> Lsp4jClient>("FAKE_LSP_CREATE_CLIENT_KEY")
internal val FAKE_LSP_IS_SUPPORTED_FILE_KEY = Key.create<(VirtualFile) -> Boolean>("FAKE_LSP_IS_SUPPORTED_FILE_KEY")

internal class FakeLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    val customization = project.getUserData(FAKE_LSP_CUSTOMIZATION_KEY) ?: LspCustomization()
    val configureServerCapabilities = project.getUserData(FAKE_LSP_SERVER_CAPABILITIES_KEY)
    val configureClientCapabilities = project.getUserData(FAKE_LSP_CLIENT_CAPABILITIES_KEY)
    val createLsp4jClient = project.getUserData(FAKE_LSP_CREATE_CLIENT_KEY)
    val isSupportedFile = project.getUserData(FAKE_LSP_IS_SUPPORTED_FILE_KEY)
    clientStarter.ensureClientStarted(
      FakeLspClientDescriptor(project, customization, configureServerCapabilities, configureClientCapabilities, createLsp4jClient,
                              isSupportedFile))
  }
}

internal open class FakeLspClientDescriptor(
  project: Project,
  override val lspCustomization: LspCustomization,
  private val configureServerCapabilities: (ServerCapabilities.() -> Unit)?,
  private val configureClientCapabilities: (ClientCapabilities.() -> Unit)?,
  private val configureLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  private val supportedFilePredicate: ((VirtualFile) -> Boolean)? = null,
  presentableName: String = "FakeLspServer",
) : ProjectWideLspClientDescriptor(project, presentableName) {
  lateinit var server: FakeLspServer

  override fun isSupportedFile(file: VirtualFile) = supportedFilePredicate?.invoke(file) ?: true

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