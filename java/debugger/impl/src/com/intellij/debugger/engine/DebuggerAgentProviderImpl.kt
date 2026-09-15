// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine

import com.intellij.debugger.testFramework.TestDebuggerAgentArtifactsProvider
import com.intellij.execution.JavaExecutionUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.getPluginDistDirByClass
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.eel.provider.EelProviderUtil
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.utils.EelPathUtils
import com.intellij.platform.eel.provider.utils.EelProjectUtils
import com.intellij.util.BazelEnvironmentUtil
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildDependenciesJps
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

class DebuggerAgentProviderImpl : DebuggerAgentProvider {
  override fun getAgentArtifactPath(project: Project?, disposable: Disposable?): Path? {
    return if (PluginManagerCore.isRunningFromSources() && !AppMode.isRunningFromDevBuild()) {
      getDownloadedAgentPath(project, disposable)
    }
    else {
      getBundledAgentPath(project, disposable)
    }
  }

  private fun getDownloadedAgentPath(project: Project?, disposable: Disposable?): Path {
    // Source-based IDE runs use the downloaded artifact.
    try {
      val agentArtifactPath = createTemporaryAgentPath(project, disposable)
      val downloadedAgent = if (PluginManagerCore.isUnitTestMode && BazelEnvironmentUtil.isBazelTestRun()) {
        // Bazel tests run in a hermetic sandbox. The test provider resolves the agent from declared runfiles.
        getTestAgentPath()
      }
      else {
        val projectRoot = Path.of(PathManager.getHomePath())
        val iml = BuildDependenciesJps.getProjectModule(projectRoot, ARTIFACT_MODULE_NAME)
        runBlocking {
          BuildDependenciesJps.getModuleLibrarySingleRoot(
            iml = iml,
            libraryName = AGENT_LIBRARY_NAME,
            mavenRepositoryUrl = "https://cache-redirector.jetbrains.com/intellij-dependencies",
            communityRoot = BuildDependenciesCommunityRoot(Path.of(PathManager.getCommunityHomePath())),
          )
        }
      }

      // EEL cannot copy symbolic links. This copy also renames the downloaded artifact.
      Files.copy(downloadedAgent.toRealPath(), agentArtifactPath)
      return agentArtifactPath
    }
    catch (e: IOException) {
      throw RuntimeException(e)
    }
  }

  private fun getTestAgentPath(): Path {
    val providers = ServiceLoader.load(TestDebuggerAgentArtifactsProvider::class.java).toList()
    check(providers.size == 1) { "One test debugger agent provider is required" }
    return providers.single().debuggerAgentJar
  }

  private fun getBundledAgentPath(project: Project?, disposable: Disposable?): Path? {
    val pluginDir = getPluginDistDirByClass(DebuggerAgentProviderImpl::class.java)
    if (pluginDir == null || !Files.isDirectory(pluginDir)) {
      LOG.error("The debugger agent plugin directory is missing")
      return null
    }

    val bundledAgentPath = pluginDir.resolve("lib").resolve("rt").resolve(AGENT_JAR_NAME)
    if (!Files.exists(bundledAgentPath)) {
      LOG.error("The bundled debugger agent is missing: $bundledAgentPath")
      return null
    }

    val projectEelDescriptor = project?.let(EelProviderUtil::getEelDescriptor)
    if (project == null || projectEelDescriptor == LocalEelDescriptor) {
      val processedAgentPath = JavaExecutionUtil.handleSpacesInAgentPath(
        bundledAgentPath.toAbsolutePath().toString(),
        "captureAgent",
        null,
      ) { it.name.startsWith("debugger-agent") }
      return processedAgentPath?.let(Path::of)
    }

    val temporaryAgentPath = createTemporaryAgentPath(project, disposable)
    try {
      // EEL cannot copy symbolic links, so resolve the bundled agent before the transfer.
      EelPathUtils.transferLocalContentToRemote(
        bundledAgentPath.toRealPath(),
        EelPathUtils.TransferTarget.Explicit(temporaryAgentPath),
      )
    }
    catch (e: IOException) {
      LOG.error("Cannot copy the debugger agent to $temporaryAgentPath", e)
      return null
    }
    return temporaryAgentPath
  }

  private fun createTemporaryAgentPath(project: Project?, disposable: Disposable?): Path {
    val debuggerAgentDir = EelProjectUtils.createTemporaryDirectory(project, "debugger-agent", "", disposable == null)
    if (disposable != null) {
      Disposer.register(disposable) {
        try {
          FileUtilRt.deleteRecursively(debuggerAgentDir)
        }
        catch (_: IOException) {
        }
      }
    }
    // The agent manifest requires this fixed JAR name.
    return debuggerAgentDir.resolve(AGENT_JAR_NAME)
  }

  private companion object {
    private val LOG = logger<DebuggerAgentProviderImpl>()
    private const val AGENT_JAR_NAME = "debugger-agent.jar"
    private const val AGENT_LIBRARY_NAME = "debugger-agent"
    private const val ARTIFACT_MODULE_NAME = "intellij.java.debugger.agent.holder"
  }
}
