package com.intellij.tools.launch.rd

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.launch.docker.BindMount
import com.intellij.tools.launch.rd.RemoteDevLauncher.logger
import com.intellij.tools.launch.rd.components.runCodeWithMeHostNoLobby
import com.intellij.tools.launch.rd.components.runJetBrainsClientLocally
import com.intellij.tools.launch.rd.dsl.*
import java.nio.file.Path

suspend fun launchRemoteDev(init: LaunchRemoteDevBuilder.() -> Unit) {
  LaunchRemoteDevBuilderImpl().let {
    it.init()
    it.launch()
  }
}

private class LaunchRemoteDevBuilderImpl : LaunchRemoteDevBuilder {
  private lateinit var clientResult: JetBrainsClientBuilderImpl.Result
  private lateinit var backendInEnvDescription: BackendInEnvDescription

  override fun client(init: JetBrainsClientBuilder.() -> Unit) {
    clientResult = JetBrainsClientBuilderImpl().let {
      it.init()
      it.build()
    }
  }

  override fun docker(init: DockerBuilder.() -> Unit) {
    backendInEnvDescription = DockerBuilderImpl().let {
      it.init()
      it.build()
    }
  }

  override fun backend(product: Product, init: BackendBuilder.() -> Unit) {
    backendInEnvDescription = BackendOnLocalMachine(
      backendDescription = BackendBuilderImpl(product, launchInDocker = false).let {
        it.init()
        it.build()
      }
    )
  }

  suspend fun launch() {
    logger.info("Starting backend: $backendInEnvDescription")
    val backendStatus = runCodeWithMeHostNoLobby(backendInEnvDescription)
    val backendDebugPort = 5006
    logger.info("Attaching debugger to backend at port $backendDebugPort")
    backendInEnvDescription.backendDescription.attachDebuggerCallback?.invoke(backendDebugPort)
    if (backendStatus.waitForHealthy()) {
      logger.info("Backend is healthy now")
    }
    else {
      error("Backend failed to start normally")
    }
    logger.info("Starting JetBrains client")
    val clientDebugPort = runJetBrainsClientLocally()
    clientResult.attachDebuggerCallback?.let {
      logger.info("Attaching debugger to client at port $clientDebugPort")
      it.invoke(clientDebugPort)
    }
  }
}

internal sealed interface BackendInEnvDescription {
  val backendDescription: BackendDescription
}

internal data class BackendOnLocalMachine(
  override val backendDescription: BackendDescription,
) : BackendInEnvDescription

internal data class BackendInDockerContainer(
  override val backendDescription: BackendDescription,
  val bindMounts: List<BindMount>,
  val image: String,
) : BackendInEnvDescription

private abstract class IdeBuilderImpl : IdeBuilder {
  protected var attachDebuggerCallback: ((Int) -> Unit)? = null
    private set

  override fun attachDebugger(callback: (Int) -> Unit) {
    attachDebuggerCallback = callback
  }
}

private class JetBrainsClientBuilderImpl : IdeBuilderImpl(), JetBrainsClientBuilder {
  fun build(): Result = Result(attachDebuggerCallback)

  data class Result(val attachDebuggerCallback: ((Int) -> Unit)?)
}

private class DockerBuilderImpl : DockerBuilder {
  private lateinit var image: String
  private val bindMounts = mutableListOf<BindMount>()
  private lateinit var dockerBackendDescription: BackendDescription

  override fun image(name: String) {
    image = name
  }

  override fun bindMount(hostPath: Path, containerPath: String, readonly: Boolean) {
    bindMounts.add(BindMount(hostPath, containerPath, readonly))
  }

  override fun backend(product: Product, init: DockerBackendBuilder.() -> Unit) {
    dockerBackendDescription = DockerBackendBuilderImpl(product).let {
      it.init()
      it.build()
    }
  }

  fun build(): BackendInDockerContainer = BackendInDockerContainer(dockerBackendDescription, bindMounts, image)
}

private open class BackendBuilderImpl(val product: Product, val launchInDocker: Boolean) : IdeBuilderImpl(), BackendBuilder {
  lateinit var projectPath: String

  override fun project(path: String) {
    projectPath = path
  }

  fun build(): BackendDescription = BackendDescription(product, launchInDocker, projectPath, attachDebuggerCallback)
}

internal data class BackendDescription(
  val product: Product,
  val launchInDocker: Boolean,
  val projectPath: String,
  val attachDebuggerCallback: ((Int) -> Unit)?,
)

private class DockerBackendBuilderImpl(product: Product) : BackendBuilderImpl(product, launchInDocker = true), DockerBackendBuilder

private object RemoteDevLauncher {
  val logger = logger<RemoteDevLauncher>()
}