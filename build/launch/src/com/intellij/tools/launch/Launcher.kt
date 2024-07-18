package com.intellij.tools.launch

import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.ide.*
import com.intellij.tools.launch.ide.environments.docker.legacyDockerRunCliCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.LocalIdeCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.localLaunchOptions
import com.intellij.tools.launch.os.ProcessOutputStrategy
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.jps.model.java.JpsJavaClasspathKind

object Launcher {
  @Suppress("SSBasedInspection")
  private val launcherLifespanScope = CoroutineScope(CoroutineName("IDE Launcher"))

  fun launch(
    paths: PathsProvider,
    modulesToScopes: Map<String, JpsJavaClasspathKind>,
    options: LauncherOptions,
    logClasspath: Boolean,
  ): Pair<Process, String?> {
    val classpath = ClassPathBuilder(paths, modulesToScopes).buildClasspath(logClasspath)
    return launch(paths, collectedClasspath(classpath), options)
  }

  fun launch(
    paths: PathsProvider,
    classpathCollector: ClasspathCollector,
    options: LauncherOptions,
  ): Pair<Process, String?> {
    val currentUserIsNotRoot = true
    val ideLaunchContext = IdeLaunchContext(
      classpathCollector = classpathCollector,
      localPaths = paths,
      ideDebugOptions = options.debugPort?.let { debugPort -> IdeDebugOptions(debugPort, options.debugSuspendOnStart, "*:") },
      platformPrefix = options.platformPrefix,
      productMode = options.productMode,
      xmx = options.xmx,
      javaArguments = options.javaArguments,
      ideaArguments = options.ideaArguments,
      environment = options.environment,
      specifyUserHomeExplicitly = options is DockerLauncherOptions && currentUserIsNotRoot
    )
    if (options is DockerLauncherOptions) {
      assert(SystemInfo.isLinux) { "Launching IDE backend from tests is now only supported on Linux" }
      val dockerLauncherFactory = legacyDockerRunCliCommandLauncherFactory(options, paths)
      val launchCommand = IdeLauncher.launchCommand(dockerLauncherFactory, ideLaunchContext)
      return launchCommand.process to launchCommand.containerId
    }
    else {
      val localLauncherFactory = LocalIdeCommandLauncherFactory(
        localLaunchOptions(
          beforeProcessStart = options.beforeProcessStart,
          processOutputStrategy = if (options.redirectOutputIntoParentProcess) ProcessOutputStrategy.InheritIO else ProcessOutputStrategy.RedirectToFiles(paths.logFolder),
          processTitle = "IDE backend (local process)",
          lifespanScope = launcherLifespanScope
        )
      )
      return IdeLauncher.launchCommand(localLauncherFactory, ideLaunchContext).process to null
    }
  }
}
