// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.server

import com.intellij.compiler.YourKitProfilerService
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.platform.eel.*
import com.intellij.platform.eel.provider.*
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.forwardLocalServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.future.asCompletableFuture
import java.nio.charset.Charset
import java.nio.file.FileSystems
import java.nio.file.Path
import kotlin.io.path.name

class EelBuildCommandLineBuilder(val project: Project, exePath: Path) : BuildCommandLineBuilder {
  companion object {
    private val logger = logger<EelBuildCommandLineBuilder>()
  }

  private val eel: EelApi = exePath.getEelDescriptor().toEelApiBlocking()
  private val commandLine = GeneralCommandLine().withExePath(exePath.toString())

  private val workingDirectory: Path = getSystemSubfolder(BuildManager.SYSTEM_ROOT)
  private val cacheDirectory: Path = getSystemSubfolder("jps-${ApplicationInfo.getInstance().getBuild()}")

  override fun addParameter(parameter: String) {
    commandLine.addParameter(parameter)
  }

  override fun addPathParameter(prefix: String, path: String) {
    addPathParameter(prefix, Path.of(path))
  }

  override fun addPathParameter(prefix: String, path: Path) {
    commandLine.addParameter(prefix + path.asEelPath())
  }

  override fun addClasspathParameter(classpathInHost: List<String>, classpathInTarget: List<String>) {
    val mappedClasspath = classpathInHost.mapNotNull { hostLocation ->
      runCatching {
        copyProjectSpecificPathToTargetIfRequired(project, Path.of(hostLocation)).asEelPath()
      }.onFailure { error -> logger.warn("Can't map classpath parameter: $hostLocation", error) }.getOrNull()
    }.joinToString(eel.platform.osFamily.pathSeparator)
    require(classpathInTarget.isEmpty()) {
      "Target classpath is not supported"
    }
    commandLine.addParameter(mappedClasspath)
  }

  override fun getWorkingDirectory(): String {
    val path = workingDirectory.asEelPath()
    return path.toString()
  }

  override fun getHostWorkingDirectory(): Path {
    return workingDirectory
  }

  override fun copyProjectAgnosticPathToTargetIfRequired(path: Path): Path {
    if (path.getEelDescriptor() != LocalEelDescriptor) {
      return path
    }
    val remotePath = workingDirectory.resolve(path.name)
    return EelPathUtils.transferLocalContentToRemote(path, EelPathUtils.TransferTarget.Explicit(remotePath))
  }

  override fun copyProjectSpecificPathToTargetIfRequired(project: Project, path: Path): Path {
    if (path.getEelDescriptor() != LocalEelDescriptor) {
      return path
    }
    val cacheFileName = project.getProjectCacheFileName()
    val target = cacheDirectory.resolve(cacheFileName).resolve(path.name)
    return EelPathUtils.transferLocalContentToRemote(path, EelPathUtils.TransferTarget.Explicit(target))
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
    // todo IJPL-173737
  }

  fun pathPrefixes(): Set<String> {
    return eel.descriptor.routingPrefixes().map { it.toString().removeSuffix(FileSystems.getDefault().separator) }.toSet()
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

  private fun getSystemSubfolder(subfolder: String): Path {
    return getSystemFolderRoot().resolve(subfolder)
  }

  private fun getSystemFolderRoot(): Path {
    return EelPathUtils.getSystemFolder(eel)
  }
}

@Service(Service.Level.PROJECT)
private class EelBuildManagerScopeProvider(val scope: CoroutineScope)