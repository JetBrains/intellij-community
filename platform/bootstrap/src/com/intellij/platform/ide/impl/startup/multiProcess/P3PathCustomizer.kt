// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.util.Restarter
import com.intellij.util.system.OS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException

@ApiStatus.Experimental
class P3PathCustomizer : PathCustomizer {
  companion object {
    const val OPTION_NAME: String = "p3.project.location"
  }

  override fun customizePaths(args: List<String>): PathCustomizer.CustomPaths {
    var projectLocation = System.getProperty(OPTION_NAME)
    if (projectLocation.isNullOrEmpty()) {
      projectLocation = "JetBrains_P3_Default_Project_Location"
    }

    val projectStoreBaseDir = Path.of(projectLocation)
    val paths = P3PathsEx(projectStoreBaseDir)
    Files.createDirectories(paths.getConfigDir())

    P3SupportInstaller.installPerProcessInstanceSupportImplementation(P3SupportImpl(projectStoreBaseDir))
    PerProcessPathCustomization.prepareConfig(paths.getConfigDir(), PathManager.getConfigDir(), false)

    if (ApplicationManagerEx.isInIntegrationTest()) {
      // write current PID to file to kill the process if it hangs
      val pid = ProcessHandle.current().pid()

      val file = paths.getSystemDir().resolve("pids.txt")
      Files.createDirectories(file.parent)
      Files.writeString(file, pid.toString())
      thisLogger().info("current pid: $pid, has been written to pids tile: $file")
    }

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

  override fun canBeOpenedInThisProcess(projectStoreBaseDir: Path): Boolean = try {
    Files.isSameFile(currentProjectStoreBaseDir, projectStoreBaseDir)
  }
  catch (_: IOException) {
    false
  }

  override suspend fun openInChildProcess(projectStoreBaseDir: Path) {

    try {
      withContext(Dispatchers.IO) {
        val command = openProjectInstanceCommand(projectStoreBaseDir)
        @Suppress("IO_FILE_USAGE")
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

  private fun openProjectInstanceCommand(projectStoreBaseDir: Path): List<String> = when (OS.CURRENT) {
    OS.Windows -> windowsOpenProjectInstanceCommand(projectStoreBaseDir)
    OS.macOS -> macOpenProjectInstanceCommand(projectStoreBaseDir)
    OS.Linux -> linuxOpenProjectInstanceCommand(projectStoreBaseDir)
    else -> emptyList()
  }

  private fun macOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> = buildList {
    add("open")
    add("-n")
    add(Restarter.getIdeStarter().toString())
    add("--args")
    addAll(openProjectInstanceArgs(projectStoreBaseDir))
    add(projectStoreBaseDir.toString())
  }

  private fun linuxOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> = buildList {
    add(Restarter.getIdeStarter().toString())
    addAll(openProjectInstanceArgs(projectStoreBaseDir))
    add(projectStoreBaseDir.toString())
  }

  private fun windowsOpenProjectInstanceCommand(projectStoreBaseDir: Path): List<String> = buildList {
    add(Restarter.getIdeStarter().toString())
    addAll(openProjectInstanceArgs(projectStoreBaseDir))
    add(projectStoreBaseDir.toString())
  }

  private fun openProjectInstanceArgs(projectStoreBaseDir: Path): List<String> = buildList {
    val map = mutableMapOf(
      PathManager.PROPERTY_SYSTEM_PATH to PathManager.getOriginalSystemDir(),
      PathManager.PROPERTY_CONFIG_PATH to PathManager.getOriginalConfigDir(),
      PathManager.PROPERTY_LOG_PATH to PathManager.getOriginalLogDir(),
      PathManager.PROPERTY_PLUGINS_PATH to PathManager.getPluginsDir(),
      PathManager.SYSTEM_PATHS_CUSTOMIZER to P3PathCustomizer::class.qualifiedName,
      P3PathCustomizer.OPTION_NAME to projectStoreBaseDir
    )

    if (System.getProperty("idea.trust.all.projects").orEmpty().isNotEmpty()) {
      map["idea.trust.all.projects"] = System.getProperty("idea.trust.all.projects")
    }

    if (ApplicationManagerEx.isInIntegrationTest()) {
      val p3PathsEx = P3PathsEx(projectStoreBaseDir)
      map["-Dtestscript.filename"] = p3PathsEx.getSystemDir().resolve(PER_PROJECT_INSTANCE_TEST_SCRIPT)
      // Do not write metrics from the 2nd+ instances to the main json file
      map["-Didea.diagnostic.opentelemetry.file"] = p3PathsEx.getLogDir().resolve("opentelemetry.json")
      map["idea.is.integration.test"] = "true"
    }

    map.forEach { (key, value) -> add("-D$key=$value") }
  }
}
