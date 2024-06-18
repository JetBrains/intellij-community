package com.intellij.tools.launch.rd

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.launch.docker.BindMount
import com.intellij.tools.launch.os.ProcessOutputInfo
import com.intellij.tools.launch.os.terminal.AnsiColor
import com.intellij.tools.launch.os.terminal.colorize
import com.intellij.tools.launch.rd.RemoteDevLauncher.logger
import com.intellij.tools.launch.rd.components.BackendLaunchResult
import com.intellij.tools.launch.rd.components.runCodeWithMeHostNoLobby
import com.intellij.tools.launch.rd.components.runJetBrainsClientLocally
import com.intellij.tools.launch.rd.dsl.*
import kotlinx.coroutines.*
import java.nio.file.Path

suspend fun CoroutineScope.launchRemoteDev(init: LaunchRemoteDevBuilder.() -> Unit): LaunchRemoteDevResult =
  LaunchRemoteDevBuilderImpl().let {
    it.init()
    it.launch(remoteDevLifespanScope = this)
  }

data class LaunchRemoteDevResult(val clientTerminationDeferred: Deferred<Int>, val backendTerminationDeferred: Deferred<Int>)

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

  suspend fun launch(remoteDevLifespanScope: CoroutineScope): LaunchRemoteDevResult {
    logger.info("Starting backend: $backendInEnvDescription")
    val backendLaunchResult = runCodeWithMeHostNoLobby(backendInEnvDescription, remoteDevLifespanScope)
    handleBackendProcessOutput(backendLaunchResult, remoteDevLifespanScope)
    val backendDebugPort = 5006
    logger.info("Attaching debugger to backend at port $backendDebugPort")
    backendInEnvDescription.backendDescription.attachDebuggerCallback?.invoke(backendDebugPort)
    if (backendLaunchResult.backendStatus.waitForHealthy()) {
      logger.info("Backend is healthy now")
    }
    else {
      error("Backend failed to start normally")
    }
    logger.info("Starting JetBrains client")
    val (clientLocalProcessResult, clientDebugPort) =
      withContext(CoroutineName("JetBrains Client Launcher")) {
        runJetBrainsClientLocally(remoteDevLifespanScope)
      }
    handleLocalProcessOutput(
      clientLocalProcessResult.processWrapper.processOutputInfo,
      processShortName = "\uD83D\uDE80 JetBrains Client",
      processColor = AnsiColor.GREEN,
      remoteDevLifespanScope
    )
    clientResult.attachDebuggerCallback?.let {
      logger.info("Attaching debugger to client at port $clientDebugPort")
      it.invoke(clientDebugPort)
    }
    return LaunchRemoteDevResult(
      clientTerminationDeferred = clientLocalProcessResult.processWrapper.terminationDeferred,
      backendTerminationDeferred = backendLaunchResult.asyncAwaitExit(remoteDevLifespanScope)
    )
  }
}

private suspend fun handleBackendProcessOutput(backendLaunchResult: BackendLaunchResult, coroutineScope: CoroutineScope) {
  when (backendLaunchResult) {
    is BackendLaunchResult.Local -> handleLocalProcessOutput(
      backendLaunchResult.localProcessResult.processWrapper.processOutputInfo,
      processShortName = "IDE Backend",
      processColor = AnsiColor.PURPLE,
      coroutineScope
    )
    is BackendLaunchResult.Docker -> handleLocalProcessOutput(
      backendLaunchResult.localDockerRunResult.processWrapper.processOutputInfo,
      processShortName = "\uD83D\uDC33 IDE Backend",
      processColor = AnsiColor.CYAN,
      coroutineScope
    )
  }
}

private fun handleLocalProcessOutput(
  outputStrategy: ProcessOutputInfo,
  processShortName: String,
  processColor: AnsiColor,
  coroutineScope: CoroutineScope,
) {
  val prefix = colorize("$processShortName\t| ", processColor)
  when (outputStrategy) {
    is ProcessOutputInfo.Piped -> {
      coroutineScope.launch(Dispatchers.IO + SupervisorJob() + CoroutineName("$processShortName | stdout")) {
        outputStrategy.outputFlows.stdout.collect { line ->
          println("$prefix$line")
        }
      }
      coroutineScope.launch(Dispatchers.IO + SupervisorJob() + CoroutineName("$processShortName | stderr")) {
        outputStrategy.outputFlows.stderr.collect { line ->
          println("$prefix${colorize(line, AnsiColor.RED)}")
        }
      }
    }
    ProcessOutputInfo.InheritedByParent -> Unit
    is ProcessOutputInfo.RedirectedToFiles -> {
      logger.info("JetBrains Client's standard streams redirected to:\n" +
                  "${outputStrategy.stdoutLogPath.toUri()}\n" +
                  "${outputStrategy.stderrLogPath.toUri()}")
    }
  }
}

private suspend fun BackendLaunchResult.asyncAwaitExit(coroutineScope: CoroutineScope): Deferred<Int> =
  when (this) {
    is BackendLaunchResult.Local -> localProcessResult.processWrapper.terminationDeferred
    is BackendLaunchResult.Docker -> localDockerRunResult.processWrapper.terminationDeferred
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
  var attachDebuggerCallback: (suspend (Int) -> Unit)? = null
    private set

  override fun attachDebugger(callback: suspend (Int) -> Unit) {
    attachDebuggerCallback = callback
  }
}

private class JetBrainsClientBuilderImpl : IdeBuilderImpl(), JetBrainsClientBuilder {
  fun build(): Result = Result(attachDebuggerCallback)

  data class Result(val attachDebuggerCallback: (suspend (Int) -> Unit)?)
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
  var jbrPath: String? = null
  lateinit var projectPath: String

  override fun jbr(path: String) {
    jbrPath = path
  }

  override fun project(path: String) {
    projectPath = path
  }

  fun build(): BackendDescription = BackendDescription(product, launchInDocker, jbrPath, projectPath, attachDebuggerCallback)
}

internal data class BackendDescription(
  val product: Product,
  val launchInDocker: Boolean,
  val jbrPath: String?,
  val projectPath: String,
  val attachDebuggerCallback: (suspend (Int) -> Unit)?,
)

private class DockerBackendBuilderImpl(product: Product) : BackendBuilderImpl(product, launchInDocker = true), DockerBackendBuilder

private object RemoteDevLauncher {
  val logger = logger<RemoteDevLauncher>()
}