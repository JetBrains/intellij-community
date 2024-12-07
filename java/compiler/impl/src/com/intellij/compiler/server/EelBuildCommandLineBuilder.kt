// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.codeInsight.navigation.LOG
import com.intellij.compiler.YourKitProfilerService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.EelTunnelsApi
import com.intellij.platform.eel.LocalEelApi
import com.intellij.platform.eel.pathSeparator
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.forwardLocalServer
import com.intellij.platform.eel.provider.getEelApiBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name

class EelBuildCommandLineBuilder(val project: Project, exePath: Path) : BuildCommandLineBuilder {
  private val eel: EelApi = exePath.getEelApiBlocking()
  private val commandLine = GeneralCommandLine().withExePath(eel.mapper.getOriginalPath(exePath).toString())

  private val workingDirectory: Path = run {
    val selector = PathManager.getPathsSelector() ?: "IJ-Platform"
    PathManager.getDefaultSystemPathFor(eel.platform.asPathManagerOs(), eel.mapper.toNioPath(eel.userInfo.home).toString(), selector)
  }

  override fun addParameter(parameter: String) {
    commandLine.addParameter(parameter)
  }

  override fun addPathParameter(prefix: String, path: String) {
    addPathParameter(prefix, Path.of(path))
  }

  override fun addPathParameter(prefix: String, path: Path) {
    commandLine.addParameter(prefix + eel.mapper.getOriginalPath(path).toString())
  }

  override fun addClasspathParameter(classpathInHost: List<String>, classpathInTarget: List<String>) {
    val mappedClasspath = classpathInHost.joinToString(eel.platform.pathSeparator) { hostLocation ->
      copyPathToHostIfRequired(Path.of(hostLocation))
    }
    require(classpathInTarget.isEmpty()) {
      "Target classpath is not supported"
    }
    commandLine.addParameter(mappedClasspath)
  }

  override fun getWorkingDirectory(): String {
    return eel.mapper.getOriginalPath(workingDirectory).toString()
  }

  override fun getHostWorkingDirectory(): Path {
    return workingDirectory
  }

  override fun copyPathToTargetIfRequired(path: Path): Path {
    if (eel is LocalEelApi) return path
    if (path.getEelApiBlocking() !is LocalEelApi) return path
    // todo: intergrate checksums here so that files could be refreshed in case of changes
    val newPath = workingDirectory.resolve("build-cache").resolve(path.name)
    if (!Files.exists(newPath)) {
      EelPathUtils.walkingTransfer(path, newPath, false, true)
    }
    return newPath
  }

  override fun copyPathToHostIfRequired(path: Path): String {
    return eel.mapper.getOriginalPath(copyPathToTargetIfRequired(path)).toString()
  }

  override fun getYjpAgentPath(yourKitProfilerService: YourKitProfilerService?): String? {
    return null
  }

  override fun setCharset(charset: Charset?) {
    if (charset != null) {
      commandLine.charset = charset
    }
  }

  override fun buildCommandLine(): GeneralCommandLine? {
    return commandLine.withWorkingDirectory(workingDirectory)
  }

  override fun setUnixProcessPriority(priority: Int) {
    // todo
  }

  fun pathPrefix(): String {
    return eel.mapper.pathPrefix()
  }

  /**
   * Ensures that connections from the environment of the build process can reach `localhost:[localPort]`
   */
  fun maybeRunReverseTunnel(localPort: Int, project: Project): Int {
    if (eel is LocalEelApi) {
      return localPort
    }
    val remoteServer = project.service<EelBuildManagerScopeProvider>().scope
      .forwardLocalServer(eel.tunnels, localPort, EelTunnelsApi.HostAddress.Builder().build())
    return remoteServer.asCompletableFuture().get().port.toInt()
  }

  fun EelPlatform.asPathManagerOs(): PathManager.OS =
    when (this) {
      is EelPlatform.Windows -> PathManager.OS.WINDOWS
      is EelPlatform.Darwin -> PathManager.OS.MACOS
      is EelPlatform.Linux -> PathManager.OS.LINUX
    }
}

@Service(Service.Level.PROJECT)
private class EelBuildManagerScopeProvider(val scope: CoroutineScope)