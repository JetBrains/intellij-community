// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Main")
@file:Suppress("ReplacePutWithAssignment")
package com.intellij.idea

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(rawArgs: Array<String>) {
  val startupTimings = LinkedHashMap<String, Long>(6)
  startupTimings.put("startup begin", System.nanoTime())

  val args = if (rawArgs.size == 1 && rawArgs[0] == "%f") emptyArray() else rawArgs
  AppMode.setFlags(args)
  try {
    bootstrap(startupTimings)
    start(args)
  }
  catch (e: Throwable) {
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), e)
    exitProcess(AppExitCodes.STARTUP_EXCEPTION)
  }
}

private fun bootstrap(startupTimings: LinkedHashMap<String, Long>) {
  startupTimings.put("properties loading", System.nanoTime())
  PathManager.loadProperties()

  startupTimings.put("plugin updates install", System.nanoTime())
  // this check must be performed before system directories are locked
  if (!AppMode.isCommandLine() || java.lang.Boolean.getBoolean(AppMode.FORCE_PLUGIN_UPDATES)) {
    val configImportNeeded = !AppMode.isHeadless() && !Files.exists(Path.of(PathManager.getConfigPath()))
    if (!configImportNeeded) {
      // Consider following steps:
      // - user opens settings, and installs some plugins;
      // - the plugins are downloaded and saved somewhere;
      // - IDE prompts for restart;
      // - after restart, the plugins are moved to proper directories ("installed") by the next line.
      // TODO get rid of this: plugins should be installed before restarting the IDE
      installPluginUpdates()
    }
  }

  startupTimings.put("classloader init", System.nanoTime())
  BootstrapClassLoaderUtil.initClassLoader(AppMode.isIsRemoteDevHost())
  StartUpMeasurer.addTimings(startupTimings, "bootstrap")
}

@Suppress("HardCodedStringLiteral")
private fun installPluginUpdates() {
  try {
    // referencing `StartupActionScriptManager` is ok - a string constant will be inlined
    val scriptFile = Path.of(PathManager.getPluginTempPath(), StartupActionScriptManager.ACTION_SCRIPT_FILE)
    if (Files.isRegularFile(scriptFile)) {
      // load StartupActionScriptManager and all others related class (ObjectInputStream and so on loaded as part of class define)
      // only if there is an action script to execute
      StartupActionScriptManager.executeActionScript()
    }
  }
  catch (e: IOException) {
    StartupErrorReporter.showMessage(
      "Plugin Installation Error",
      """
       The IDE failed to install or update some plugins.
       Please try again, and if the problem persists, please report it
       to https://jb.gg/ide/critical-startup-errors
       
       The cause: $e
     """.trimIndent(),
      false
    )
  }
}
