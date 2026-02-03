package com.intellij.tools.launch.ide.environments.docker

import com.intellij.tools.launch.DockerLauncherOptions
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.AbstractCommandLauncher
import com.intellij.tools.launch.environments.LaunchCommand
import com.intellij.tools.launch.environments.LaunchEnvironment
import com.intellij.tools.launch.ide.ClassPathBuilder
import com.intellij.tools.launch.ide.ClasspathCollector
import com.intellij.tools.launch.ide.IdeCommandLauncherFactory
import com.intellij.tools.launch.ide.IdePathsInLaunchEnvironment
import com.intellij.tools.launch.os.ProcessOutputInfo
import com.intellij.tools.launch.os.ProcessWrapper
import com.intellij.tools.launch.os.asyncAwaitExit
import com.intellij.tools.launch.os.produceOutputFlows
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Obsolete
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

interface LocalDockerRunResult {
  /**
   * Use [processWrapper] instead. If [ProcessWrapper] lacks certain properties feel consider adding them instead of switching to [process].
   */
  @get:Obsolete
  val process: Process
  val processWrapper: ProcessWrapper
  val containerId: String
}

/**
 * Note! [process] is still used in [com.intellij.tools.launch.Launcher]. [processWrapperFactory] factory allows on-demand consumption of
 * the process output without interfering with the direct usages of input and error streams of the process.
 */
private data class LocalDockerRunResultImpl(
  private val underlyingProcess: Process,
  override val containerId: String,
  private val processWrapperFactory: (Process) -> ProcessWrapper,
) : LocalDockerRunResult {
  private var _processWrapper: ProcessWrapper? = null

  /**
   * Guards modification of [_processWrapper] and access to [process] and [processWrapper].
   */
  private val lock = ReentrantLock()

  override val process: Process
    get() = lock.withLock {
      require(_processWrapper == null) { "`process` is wrapped, use `processWrapper` instead" }
      underlyingProcess
    }

  override val processWrapper: ProcessWrapper
    get() = lock.withLock {
      _processWrapper ?: processWrapperFactory(underlyingProcess).also { _processWrapper = it }
    }
}

fun dockerRunCliCommandLauncherFactory(dockerContainerOptions: DockerContainerOptions): DockerRunCliCommandLauncherFactory {
  val dockerLauncherOptions = DockerLauncherOptionsImpl(
    dockerImageName = dockerContainerOptions.image,
    containerName = dockerContainerOptions.containerName
  )
  return DockerRunCliCommandLauncherFactory(dockerContainerOptions, dockerLauncherOptions)
}

fun legacyDockerRunCliCommandLauncherFactory(
  dockerLauncherOptions: DockerLauncherOptions,
  paths: PathsProvider,
): DockerRunCliCommandLauncherFactory {
  val dockerContainerOptions = DockerContainerOptions(
    image = dockerLauncherOptions.dockerImageName,
    containerName = dockerLauncherOptions.containerName,
    javaExecutable = paths.javaExecutable.canonicalPath,
    ultimateRepositoryPathInContainer = paths.sourcesRootFolder.canonicalPath,
    legacy = true
  )
  return DockerRunCliCommandLauncherFactory(dockerContainerOptions, dockerLauncherOptions)
}

class DockerRunCliCommandLauncherFactory(
  private val dockerContainerOptions: DockerContainerOptions,
  private val dockerLauncherOptions: DockerLauncherOptions,
) : IdeCommandLauncherFactory<LocalDockerRunResult> {
  override fun create(
    localPaths: PathsProvider,
    classpathCollector: ClasspathCollector,
  ): Pair<AbstractCommandLauncher<LocalDockerRunResult>, IdePathsInLaunchEnvironment> {
    val dockerContainerEnvironment = DockerContainerEnvironment.createDefaultDockerContainerEnvironment(dockerContainerOptions, localPaths)
    // collect classpath with the paths within the Docker container (the container does not exist at this point, it is going to be created later)
    val classpathInDocker = classpathCollector.collect(dockerContainerEnvironment)
    // create the classpath file locally, later it will be mounted inside the Docker container
    // at this point we know that the file is going to be created inside `localPaths.logFolder` directory
    val classPathArgFile = ClassPathBuilder.createClassPathArgFile(localPaths, classpathInDocker, UNIX_PATH_SEPARATOR.toString())
    assert(classPathArgFile.startsWith(localPaths.logFolder)) {
      "IDE's classpath arg file '$classPathArgFile' is expected to be created within the log folder '${localPaths.logFolder}'"
    }
    val paths = createIdePathsInDockerEnvironment(localPaths, dockerContainerOptions, classPathArgFile)
    return DockerRunCliCommandLauncher(localPaths, dockerContainerEnvironment, dockerContainerOptions, dockerLauncherOptions) to paths
  }
}

private class DockerRunCliCommandLauncher(
  val localPaths: PathsProvider,
  val dockerContainerEnvironment: DockerContainerEnvironment,
  val dockerContainerOptions: DockerContainerOptions,
  val dockerLauncherOptions: DockerLauncherOptions,
) : AbstractCommandLauncher<LocalDockerRunResult> {
  override fun launch(buildCommand: LaunchEnvironment.() -> LaunchCommand): LocalDockerRunResult {
    val launchCommand = dockerContainerEnvironment.buildCommand()
    val dockerLauncher = DockerLauncher(localPaths, dockerLauncherOptions)
    val (process, containerId) = dockerLauncher.runInContainer(dockerContainerEnvironment, launchCommand, dockerContainerOptions)
    return LocalDockerRunResultImpl(process, containerId, processWrapperFactory(processTitle = "Docker container ($containerId)"))
  }
}

@Suppress("SSBasedInspection")
private fun createLifespanScope(processTitle: String): CoroutineScope = CoroutineScope(CoroutineName(processTitle))

private fun processWrapperFactory(processTitle: String): (Process) -> ProcessWrapper =
  { process ->
    val lifespanScope = createLifespanScope(processTitle)
    ProcessWrapper(
      processOutputInfo = ProcessOutputInfo.Piped(process.produceOutputFlows(lifespanScope)),
      terminationDeferred = process.asyncAwaitExit(lifespanScope, processTitle)
    )
  }