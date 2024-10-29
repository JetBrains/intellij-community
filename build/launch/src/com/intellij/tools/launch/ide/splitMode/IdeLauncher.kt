package com.intellij.tools.launch.ide.splitMode

import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.launch.docker.BindMount
import com.intellij.tools.launch.ide.splitMode.IdeLauncher.logger
import com.intellij.tools.launch.ide.splitMode.dsl.*
import com.intellij.tools.launch.os.ProcessOutputInfo
import com.intellij.tools.launch.os.terminal.AnsiColor
import com.intellij.tools.launch.os.terminal.colorize
import kotlinx.coroutines.*
import java.nio.file.Path

suspend fun CoroutineScope.launchIde(init: LaunchIdeBuilder.() -> Unit): LaunchIdeResult =
  LaunchIdeBuilderImpl().let {
    it.init()
    it.launch(ideLifespanScope = this)
  }

data class LaunchIdeResult(val ideFrontendTerminationDeferred: Deferred<Int>, val ideBackendTerminationDeferred: Deferred<Int>)

private class LaunchIdeBuilderImpl : LaunchIdeBuilder {
  private lateinit var ideFrontendResult: IdeFrontendBuilderImpl.Result
  private lateinit var ideBackendInEnvDescription: IdeBackendInEnvDescription

  override fun frontend(product: Product, init: IdeFrontendBuilder.() -> Unit) {
    ideFrontendResult = IdeFrontendBuilderImpl(product).let {
      it.init()
      it.build()
    }
  }

  override fun docker(init: DockerBuilder.() -> Unit) {
    ideBackendInEnvDescription = DockerBuilderImpl().let {
      it.init()
      it.build()
    }
  }

  override fun backend(product: Product, init: IdeBackendBuilder.() -> Unit) {
    ideBackendInEnvDescription = IdeBackendOnLocalMachine(
      ideBackendDescription = IdeBackendBuilderImpl(product, launchInDocker = false).let {
        it.init()
        it.build()
      }
    )
  }

  suspend fun launch(ideLifespanScope: CoroutineScope): LaunchIdeResult {
    logger.info("Starting IDE backend: $ideBackendInEnvDescription")
    val backendLaunchResult = runIdeBackend(ideBackendInEnvDescription, ideLifespanScope)
    handleBackendProcessOutput(backendLaunchResult, ideLifespanScope)
    val backendDebugPort = 5006
    logger.info("Attaching debugger to backend at port $backendDebugPort")
    ideBackendInEnvDescription.ideBackendDescription.attachDebuggerCallback?.invoke(backendDebugPort)
    if (backendLaunchResult.backendStatus.waitForHealthy()) {
      logger.info("IDE backend is healthy now")
    }
    else {
      error("IDE backend failed to start normally")
    }
    logger.info("Starting IDE frontend")
    val (ideFrontendLocalProcessResult, ideFrontendDebugPort) =
      withContext(CoroutineName("IDE Frontend Launcher")) {
        runIdeFrontendLocally(ideFrontendResult.product, ideLifespanScope)
      }
    handleLocalProcessOutput(
      ideFrontendLocalProcessResult.processWrapper.processOutputInfo,
      processShortName = "\uD83D\uDE80 IDE Frontend",
      processColor = AnsiColor.GREEN,
      ideLifespanScope
    )
    ideFrontendResult.attachDebuggerCallback?.let {
      logger.info("Attaching debugger to IDE frontend at port $ideFrontendDebugPort")
      it.invoke(ideFrontendDebugPort)
    }
    return LaunchIdeResult(
      ideFrontendTerminationDeferred = ideFrontendLocalProcessResult.processWrapper.terminationDeferred,
      ideBackendTerminationDeferred = backendLaunchResult.asyncAwaitExit()
    )
  }
}

private fun handleBackendProcessOutput(backendLaunchResult: BackendLaunchResult, coroutineScope: CoroutineScope) {
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
      logger.info("IDE frontend standard streams redirected to:\n" +
                  "${outputStrategy.stdoutLogPath.toUri()}\n" +
                  "${outputStrategy.stderrLogPath.toUri()}")
    }
  }
}

private fun BackendLaunchResult.asyncAwaitExit(): Deferred<Int> =
  when (this) {
    is BackendLaunchResult.Local -> localProcessResult.processWrapper.terminationDeferred
    is BackendLaunchResult.Docker -> localDockerRunResult.processWrapper.terminationDeferred
  }

internal sealed interface IdeBackendInEnvDescription {
  val ideBackendDescription: IdeBackendDescription
}

internal data class IdeBackendOnLocalMachine(
  override val ideBackendDescription: IdeBackendDescription,
) : IdeBackendInEnvDescription

internal data class IdeBackendInDockerContainer(
  override val ideBackendDescription: IdeBackendDescription,
  val bindMounts: List<BindMount>,
  val image: String,
) : IdeBackendInEnvDescription

private abstract class IdeBuilderImpl : IdeBuilder {
  var attachDebuggerCallback: (suspend (Int) -> Unit)? = null
    private set

  override fun attachDebugger(callback: suspend (Int) -> Unit) {
    attachDebuggerCallback = callback
  }
}

private class IdeFrontendBuilderImpl(private val product: Product) : IdeBuilderImpl(), IdeFrontendBuilder {
  fun build(): Result = Result(product, attachDebuggerCallback)

  data class Result(val product: Product, val attachDebuggerCallback: (suspend (Int) -> Unit)?)
}

private class DockerBuilderImpl : DockerBuilder {
  private lateinit var image: String
  private val bindMounts = mutableListOf<BindMount>()
  private lateinit var ideBackendDescription: IdeBackendDescription

  override fun image(name: String) {
    image = name
  }

  override fun bindMount(hostPath: Path, containerPath: String, readonly: Boolean) {
    bindMounts.add(BindMount(hostPath, containerPath, readonly))
  }

  override fun backend(product: Product, init: IdeBackendInDockerBuilder.() -> Unit) {
    ideBackendDescription = IdeBackendInDockerBuilderImpl(product).let {
      it.init()
      it.build()
    }
  }

  fun build(): IdeBackendInDockerContainer = IdeBackendInDockerContainer(ideBackendDescription, bindMounts, image)
}

private open class IdeBackendBuilderImpl(val product: Product, val launchInDocker: Boolean) : IdeBuilderImpl(), IdeBackendBuilder {
  var jbrPath: String? = null
  var projectPath: String? = null

  override fun jbr(path: String) {
    jbrPath = path
  }

  override fun project(path: String) {
    projectPath = path
  }

  fun build(): IdeBackendDescription = IdeBackendDescription(product, launchInDocker, jbrPath, projectPath, attachDebuggerCallback)
}

internal data class IdeBackendDescription(
  val product: Product,
  val launchInDocker: Boolean,
  val jbrPath: String?,
  val projectPath: String?,
  val attachDebuggerCallback: (suspend (Int) -> Unit)?,
)

private class IdeBackendInDockerBuilderImpl(
  product: Product,
) : IdeBackendBuilderImpl(product, launchInDocker = true), IdeBackendInDockerBuilder

private object IdeLauncher {
  val logger = logger<IdeLauncher>()
}