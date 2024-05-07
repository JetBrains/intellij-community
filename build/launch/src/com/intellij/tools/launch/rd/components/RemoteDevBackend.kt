package com.intellij.tools.launch.rd.components

import com.intellij.openapi.application.PathManager
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.environments.PathInLaunchEnvironment
import com.intellij.tools.launch.ide.IdeDebugOptions
import com.intellij.tools.launch.ide.IdeLaunchContext
import com.intellij.tools.launch.ide.IdeLauncher
import com.intellij.tools.launch.ide.classpathCollector
import com.intellij.tools.launch.ide.environments.docker.DockerContainerOptions
import com.intellij.tools.launch.ide.environments.docker.dockerRunCliCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.LocalIdeCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.localLaunchOptions
import com.intellij.tools.launch.rd.BackendInDockerContainer
import com.intellij.tools.launch.rd.BackendInEnvDescription
import com.intellij.tools.launch.rd.BackendOnLocalMachine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val DEFAULT_CONTAINER_NAME = "idea-ultimate"
private const val DEFAULT_JAVA_EXECUTABLE_PATH: PathInLaunchEnvironment = "/usr/bin/java"

internal fun runCodeWithMeHostNoLobby(backendDescription: BackendInEnvDescription): BackendStatus {
  val projectPath = backendDescription.backendDescription.projectPath
  val mainModule = backendDescription.backendDescription.product.mainModule
  val paths = IdeaPathsProvider()
  val classpathCollector = classpathCollector(
    localPaths = paths,
    mainModule = mainModule,
    additionalRuntimeModules = listOf(RemoteDevConstants.GATEWAY_PLUGIN_MODULE)
  )
  val debugPort = 5006
  val environment = mapOf(
    "CWM_HOST_PASSWORD" to RemoteDevConstants.DEFAULT_CWM_PASSWORD,
    "CWM_NO_TIMEOUTS" to "1",
    "ORG_JETBRAINS_PROJECTOR_SERVER_ATTACH_TO_IDE" to "false",
    "DISPLAY" to ":0",
  )
  val process = when (backendDescription) {
    is BackendOnLocalMachine -> {
      IdeLauncher.launchCommand(
        LocalIdeCommandLauncherFactory(localLaunchOptions(redirectOutputIntoParentProcess = false, logFolder = paths.logFolder)),
        context = IdeLaunchContext(
          classpathCollector = classpathCollector,
          localPaths = paths,
          ideDebugOptions = IdeDebugOptions(debugPort, debugSuspendOnStart = true, bindToHost = ""),
          platformPrefix = RemoteDevConstants.IDEA_PREFIX,
          ideaArguments = cwmHostNoLobby(bindToHost = "127.0.0.1", projectPath),
          environment = environment,
          specifyUserHomeExplicitly = false,
        )
      )
    }
    is BackendInDockerContainer -> {
      IdeLauncher.launchCommand(
        dockerRunCliCommandLauncherFactory(
          DockerContainerOptions(
            image = backendDescription.image,
            containerName = DEFAULT_CONTAINER_NAME,
            javaExecutable = DEFAULT_JAVA_EXECUTABLE_PATH,
            ultimateRepositoryPathInContainer = "/intellij",
            bindMounts = backendDescription.bindMounts
          )
        ),
        context = IdeLaunchContext(
          classpathCollector = classpathCollector,
          localPaths = paths,
          ideDebugOptions = IdeDebugOptions(debugPort, debugSuspendOnStart = true, bindToHost = "*:"),
          platformPrefix = RemoteDevConstants.IDEA_PREFIX,
          ideaArguments = cwmHostNoLobby(bindToHost = "0.0.0.0", projectPath),
          environment = environment,
          specifyUserHomeExplicitly = false,
        )
      ).process
    }
  }

  return BackendStatusFromStdout(process)
}

internal interface BackendStatus {
  suspend fun waitForHealthy(): Boolean
}

private class BackendStatusFromStdout(private val process: Process) : BackendStatus {
  override suspend fun waitForHealthy(): Boolean {
    return withContext(Dispatchers.IO) {
      // do not call `use` or `useLines` to prevent the process from stopping after closing its `stdout`
      process.inputStream
        .bufferedReader()
        .lineSequence()
        .forEach { line ->
          if (line.contains("Join link:")) {
            return@withContext true
          }
        }
      false
    }
  }
}

private fun cwmHostNoLobby(bindToHost: String, projectPath: PathInLaunchEnvironment) =
  listOf("cwmHostNoLobby", "-l", bindToHost, projectPath)

private class IdeaPathsProvider : PathsProvider {
  override val productId: String
    get() = RemoteDevConstants.IDEA_PREFIX
  override val sourcesRootFolder: File
    get() = File(PathManager.getHomePath())
  override val communityRootFolder: File
    get() = sourcesRootFolder.resolve("community")
  override val outputRootFolder: File
    get() = sourcesRootFolder.resolve("out").resolve("classes")
}