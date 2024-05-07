package com.intellij.tools.launch

import com.intellij.openapi.util.SystemInfo
import com.intellij.tools.launch.ide.*
import com.intellij.tools.launch.ide.environments.docker.legacyDockerRunCliCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.LocalIdeCommandLauncherFactory
import com.intellij.tools.launch.ide.environments.local.localLaunchOptions
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import java.net.InetAddress
import java.net.ServerSocket

object Launcher {
  fun launch(paths: PathsProvider,
             modulesToScopes: Map<String, JpsJavaClasspathKind>,
             options: LauncherOptions,
             logClasspath: Boolean): Pair<Process, String?> {
    val classpath = ClassPathBuilder(paths, modulesToScopes).buildClasspath(logClasspath)
    return launch(paths, collectedClasspath(classpath), options)
  }

  fun launch(
    paths: PathsProvider,
    classpathCollector: ClasspathCollector,
    options: LauncherOptions
  ): Pair<Process, String?> {
    val currentUserIsNotRoot = true
    val ideLaunchContext = IdeLaunchContext(
      classpathCollector = classpathCollector,
      localPaths = paths,
      ideDebugOptions = options.debugPort?.let { debugPort -> IdeDebugOptions(debugPort, options.debugSuspendOnStart, "*") },
      platformPrefix = options.platformPrefix,
      productMode = options.productMode,
      xmx = options.xmx,
      javaArguments = options.javaArguments,
      ideaArguments = options.ideaArguments,
      environment = options.environment,
      specifyUserHomeExplicitly = options is DockerLauncherOptions && currentUserIsNotRoot
    )
    if (options is DockerLauncherOptions) {
      assert(SystemInfo.isLinux) { "Launching Remote Dev backend from tests is now only supported on Linux" }
      val dockerLauncherFactory = legacyDockerRunCliCommandLauncherFactory(options, paths)
      val launchCommand = IdeLauncher.launchCommand(dockerLauncherFactory, ideLaunchContext)
      return launchCommand.process to launchCommand.containerId
    }
    else {
      val localLauncherFactory = LocalIdeCommandLauncherFactory(
        localLaunchOptions(
          beforeProcessStart = options.beforeProcessStart,
          redirectOutputIntoParentProcess = options.redirectOutputIntoParentProcess,
          logFolder = paths.logFolder
        )
      )
      return IdeLauncher.launchCommand(localLauncherFactory, ideLaunchContext) to null
    }
  }

  fun findFreePort(): Int {
    synchronized(this) {
      val socket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
      val result = socket.localPort
      socket.reuseAddress = true
      socket.close()
      return result
    }
  }
}
