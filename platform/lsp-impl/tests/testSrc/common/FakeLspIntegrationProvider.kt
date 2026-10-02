package com.intellij.platform.lsp.common

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.Lsp4jClient
import com.intellij.platform.lsp.api.LspCommunicationChannel
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.LspServerNotificationsHandler
import com.intellij.platform.lsp.api.ProjectWideLspClientDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ServerCapabilities
import java.net.InetAddress
import java.net.ServerSocket

internal fun TestFixture<Project>.fakeLspIntegrationFixture(
  lspCustomization: LspCustomization = LspCustomization(),
  configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  isSupportedFile: ((VirtualFile) -> Boolean)? = null,
  offerServerLogInNotifications: Boolean = false,
  useSocket: Boolean = false,
): TestFixture<FakeLspIntegration> = testFixture { _ ->
  val projectFixture = this@fakeLspIntegrationFixture
  val project = projectFixture.init()

  check(project.getUserData(FAKE_LSP_CONFIG_KEY) == null) {
    "fakeLspIntegrationFixture is already set up for $project, probably by an enclosing test class. " +
    "Declare it either in the outer class or in each @Nested class, not in both."
  }

  extensionPointFixture(LspIntegrationProvider.EP_NAME) {
    FakeLspIntegrationProvider()
  }.init()

  project.putUserData(FAKE_LSP_CONFIG_KEY, FakeLspConfig(lspCustomization, configureClientCapabilities, configureServerCapabilities,
                                                          createLsp4jClient, isSupportedFile, offerServerLogInNotifications,
                                                          useSocket))

  initialized(FakeLspIntegration()) {
    project.putUserData(FAKE_LSP_CONFIG_KEY, null)
  }
}

internal class FakeLspIntegration {
  // todo move fun configureServerSession here
}

/** The options of [fakeLspIntegrationFixture], passed as is to every [FakeLspClientDescriptor] it starts. */
internal class FakeLspConfig(
  val lspCustomization: LspCustomization = LspCustomization(),
  val configureClientCapabilities: (ClientCapabilities.() -> Unit)? = null,
  val configureServerCapabilities: (ServerCapabilities.() -> Unit)? = null,
  val createLsp4jClient: ((LspServerNotificationsHandler) -> Lsp4jClient)? = null,
  val isSupportedFile: ((VirtualFile) -> Boolean)? = null,
  val offerServerLogInNotifications: Boolean = false,
  val useSocket: Boolean = false,
)

private val FAKE_LSP_CONFIG_KEY = Key.create<FakeLspConfig>("FAKE_LSP_CONFIG_KEY")

internal class FakeLspIntegrationProvider : LspIntegrationProvider {
  override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
    val config = project.getUserData(FAKE_LSP_CONFIG_KEY) ?: FakeLspConfig()
    val descriptor = if (config.useSocket) FakeSocketLspClientDescriptor(project, config) else FakeLspClientDescriptor(project, config)
    clientStarter.ensureClientStarted(descriptor)
  }
}

internal open class FakeLspClientDescriptor(
  project: Project,
  protected val config: FakeLspConfig = FakeLspConfig(),
  presentableName: String = "FakeLspServer",
) : ProjectWideLspClientDescriptor(project, presentableName) {
  lateinit var server: FakeLspServer

  override val lspCustomization: LspCustomization = config.lspCustomization

  override val offerServerLogInNotifications: Boolean = config.offerServerLogInNotifications

  override fun isSupportedFile(file: VirtualFile) = config.isSupportedFile?.invoke(file) ?: true

  override val clientCapabilities: ClientCapabilities
    get() = super.clientCapabilities.apply {
      config.configureClientCapabilities?.invoke(this)
    }

  override fun createLsp4jClient(handler: LspServerNotificationsHandler): Lsp4jClient =
    config.createLsp4jClient?.invoke(handler) ?: super.createLsp4jClient(handler)

  override fun createCommandLine(): GeneralCommandLine {
    /** command is usable for debugging **/
    return object : GeneralCommandLine("fake --lsp") {
      override fun startProcess(): Process {
        val fakeServer = createServer()
        server = fakeServer
        return fakeServer
      }
    }
  }

  protected open fun createServer(): FakeLspServer = FakeLspServer(config.configureServerCapabilities)
}

/** Talks LSP over a socket, so a test can write to the stdout of the server. */
internal class FakeSocketLspClientDescriptor(project: Project, config: FakeLspConfig) : FakeLspClientDescriptor(project, config) {
  // Lazy: the provider creates a descriptor for each opened file, but only the started one opens a socket. The server closes it.
  private val serverSocket: ServerSocket by lazy { ServerSocket(0, 1, InetAddress.getLoopbackAddress()) }

  override val lspCommunicationChannel: LspCommunicationChannel
    get() = LspCommunicationChannel.Socket(serverSocket.localPort)

  override fun createServer(): FakeLspServer = FakeSocketLspServer(config.configureServerCapabilities, serverSocket)
}