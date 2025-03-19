// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.startup.multiProcess

import com.intellij.openapi.application.PathCustomizer
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.ex.P3PathsEx
import com.intellij.openapi.project.impl.P3Support
import com.intellij.openapi.project.impl.P3SupportInstaller
import com.intellij.openapi.project.impl.PER_PROJECT_INSTANCE_TEST_SCRIPT
import com.intellij.openapi.project.impl.ProjectManagerImpl
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.Restarter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.div

@ApiStatus.Experimental
class P3PathCustomizer : PathCustomizer {
  companion object {
    const val optionName = "p3.project.location"
  }

  override fun customizePaths(args: List<String>): PathCustomizer.CustomPaths {
    var projectLocation = System.getProperty(optionName)
    if (projectLocation.isNullOrEmpty())
      projectLocation = "JetBrains_P3_Default_Project_Location"

    val projectStoreBaseDir = Path.of(projectLocation)
    val paths = P3PathsEx(projectStoreBaseDir)
    Files.createDirectories(paths.getConfigDir())

    PerProcessPathCustomization.prepareConfig(paths.getConfigDir(), PathManager.getConfigDir(), false)

    if (ApplicationManagerEx.isInIntegrationTest()) {
      // write current PID to file to kill the process if it hangs
      val pid = ProcessHandle.current().pid()

      @Suppress("SpellCheckingInspection")
      val file = paths.getSystemDir().resolve("pids.txt")
      Files.createDirectories(file.parent)
      Files.writeString(file, pid.toString())
      thisLogger().info("current pid: $pid, has been written to pids tile: $file")
    }

    P3SupportInstaller.installPerProcessInstanceSupportImplementation(P3SupportImpl(projectStoreBaseDir))
    return PathCustomizer.CustomPaths(
      paths.getConfigDir().toCanonicalPath(),
      paths.getSystemDir().toCanonicalPath(),
      paths.getPluginsDir().toCanonicalPath(),
      paths.getLogDir().toCanonicalPath(),
      PerProcessPathCustomization.getStartupScriptDir()
    )
  }
}

private class P3SupportImpl(private val currentProjectStoreBaseDir: Path) : P3Support {
  override fun isEnabled(): Boolean = true

  override fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean {
    return try {
      Files.isSameFile(currentProjectStoreBaseDir, projectStoreBaseDir)
    }
    catch (e: IOException) {
      return false
    }
  }

  override suspend fun openInChildProcess(projectStoreBaseDir: Path) {

    try {
      withContext(Dispatchers.IO) {
        val command = openProjectInstanceCommand(projectStoreBaseDir)
        ProcessBuilder(command)
          .redirectErrorStream(true)
          .redirectOutput(ProcessBuilder.Redirect.appendTo(PathManager.getLogDir().resolve("idea.log").toFile()))
          .start()
          .also {
            logger<ProjectManagerImpl>().info("Child process started, PID: ${it.pid()}")
          }
      }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      logger<ProjectManagerImpl>().error(e)
    }
  }

  private fun openProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
    return when {
      SystemInfo.isMac -> macOpenProjectInstanceCommand(projectStoreBaseDir)
      SystemInfo.isLinux -> linuxOpenProjectInstanceCommand(projectStoreBaseDir)
      SystemInfo.isWindows -> windowsOpenProjectInstanceCommand(projectStoreBaseDir)
      else -> emptyList()
    }
  }

  private fun macOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
    return buildList {
      add("open")
      add("-n")
      add(Restarter.getIdeStarter().toString())
      add("--args")
      addAll(openProjectInstanceArgs(projectStoreBaseDir))
      add(projectStoreBaseDir.toString())
    }
  }

  private fun linuxOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
    return buildList {
      add(Restarter.getIdeStarter().toString())
      addAll(openProjectInstanceArgs(projectStoreBaseDir))
      add(projectStoreBaseDir.toString())
    }
  }

  private fun windowsOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> {
    return buildList {
      add(Restarter.getIdeStarter().toString())
      addAll(openProjectInstanceArgs(projectStoreBaseDir))
      add(projectStoreBaseDir.toString())
    }
  }

  private fun openProjectInstanceArgs(projectStoreBaseDir: Path): List<String> {
    val buildList = buildList {
      val map = mutableMapOf(
        PathManager.PROPERTY_SYSTEM_PATH to PathManager.getOriginalSystemDir(),
        PathManager.PROPERTY_CONFIG_PATH to PathManager.getOriginalConfigDir(),
        PathManager.PROPERTY_LOG_PATH to PathManager.getOriginalLogDir(),
        PathManager.PROPERTY_PLUGINS_PATH to PathManager.getPluginsDir(),
        PathManager.SYSTEM_PATHS_CUSTOMIZER to P3PathCustomizer::class.qualifiedName,
        P3PathCustomizer.optionName to projectStoreBaseDir
      )

      if (System.getProperty("idea.trust.all.projects").orEmpty().isNotEmpty()) {
        map["idea.trust.all.projects"] = System.getProperty("idea.trust.all.projects")
      }

      if (ApplicationManagerEx.isInIntegrationTest()) {
        val p3PathsEx = P3PathsEx(projectStoreBaseDir)
        val customTestScriptPath = p3PathsEx.getSystemDir().resolve(PER_PROJECT_INSTANCE_TEST_SCRIPT)
        @Suppress("SpellCheckingInspection")
        add("-Dtestscript.filename=${customTestScriptPath}")

        // Do not write metrics from the 2nd+ instances to the main json file
        add("-Didea.diagnostic.opentelemetry.file=${p3PathsEx.getLogDir() / "opentelemetry.json"}")
        map["idea.is.integration.test"] = "true"
      }

      addAll(map.map { (key, value) -> "-D$key=$value" }.toTypedArray())
    }
    return buildList
  }
}

