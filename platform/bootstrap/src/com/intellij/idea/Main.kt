// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Main")

package com.intellij.idea

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.enableCoroutineDump
import com.intellij.diagnostic.rootTask
import com.intellij.diagnostic.runActivity
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.jetbrains.JBR
import kotlinx.coroutines.*
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

fun main(rawArgs: Array<String>) {
  val startupTimings = ArrayList<Pair<String, Long>>(6)
  startupTimings.add("startup begin" to System.nanoTime())

  val args: List<String> = preprocessArgs(rawArgs)
  AppMode.setFlags(args)

  try {
    bootstrap(startupTimings)
    startupTimings.add("main scope creating" to System.nanoTime())
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking(rootTask()) {
      StartUpMeasurer.addTimings(startupTimings, "bootstrap")
      val appInitPreparationActivity = StartUpMeasurer.startActivity("app initialization preparation")
      val busyThread = Thread.currentThread()

      if (System.getProperty("idea.enable.coroutine.dump", "true").toBoolean()) {
        launch(CoroutineName("coroutine debug probes init")) {
          enableCoroutineDump()
        }
      }

      launch(CoroutineName("ForkJoin CommonPool configuration") + Dispatchers.Default) {
        IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(AppMode.isHeadless())
      }

      // not IO-, but CPU-bound due to descrambling, don't use here IO dispatcher
      val appStarterDeferred = async(CoroutineName("main class loading") + Dispatchers.Default) {
        val aClass = AppStarter::class.java.classLoader.loadClass("com.intellij.idea.MainImpl")
        MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as AppStarter
      }

      initProjectorIfNeeded(args)
      // Must be called after projector init
      initLuxIfNeeded(args)

      withContext(Dispatchers.Default + StartupAbortedExceptionHandler()) {
        StartUpMeasurer.appInitPreparationActivity = appInitPreparationActivity
        startApplication(args = args,
                         appStarterDeferred = appStarterDeferred,
                         mainScope = this@runBlocking,
                         busyThread = busyThread)
      }

      awaitCancellation()
    }
  }
  catch (e: Throwable) {
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), e)
    exitProcess(AppExitCodes.STARTUP_EXCEPTION)
  }
}

private fun initProjectorIfNeeded(args: List<String>) {
  if (args.isEmpty() || (AppMode.CWM_HOST_COMMAND != args[0] && AppMode.CWM_HOST_NO_LOBBY_COMMAND != args[0] && AppMode.REMOTE_DEV_HOST_COMMAND != args[0])) {
    return
  }

  if (!JBR.isProjectorUtilsSupported()) {
    error("JBR version 17.0.5b653.12 or later is required to run a remote-dev server")
  }

  runActivity("cwm host init") {
    JBR.getProjectorUtils().setLocalGraphicsEnvironmentProvider(Supplier {
      val projectorEnvClass = AppStarter::class.java.classLoader.loadClass("org.jetbrains.projector.awt.image.PGraphicsEnvironment")
      projectorEnvClass.getDeclaredMethod("getInstance").invoke(null) as GraphicsEnvironment
    })

    val projectorMainClass = AppStarter::class.java.classLoader.loadClass("org.jetbrains.projector.server.ProjectorLauncher\$Starter")
    MethodHandles.privateLookupIn(projectorMainClass, MethodHandles.lookup()).findStatic(
      projectorMainClass, "runProjectorServer", MethodType.methodType(Boolean::class.javaPrimitiveType)
    ).invoke()
  }
}

private fun initLuxIfNeeded(args: List<String>) {
  if (args.isEmpty() || (AppMode.CWM_HOST_COMMAND != args[0] && AppMode.CWM_HOST_NO_LOBBY_COMMAND != args[0] && AppMode.REMOTE_DEV_HOST_COMMAND != args[0])) {
    return
  }
  if (!System.getProperty("lux.enabled").toBoolean()) {
    return
  }
  if (!JBR.isGraphicsUtilsSupported()) {
    error("JBR version 17.0.6b796 or later is required to run a remote-dev server with lux")
  }

  System.setProperty("awt.nativeDoubleBuffering", false.toString())
  System.setProperty("swing.bufferPerWindow", true.toString())
}

private fun bootstrap(startupTimings: ArrayList<Pair<String, Long>>) {
  startupTimings.add("properties loading" to System.nanoTime())
  PathManager.loadProperties()

  startupTimings.add("plugin updates install" to System.nanoTime())
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

  startupTimings.add("classloader init" to System.nanoTime())
  BootstrapClassLoaderUtil.initClassLoader(AppMode.isRemoteDevHost())
}

private fun preprocessArgs(args: Array<String>): List<String> {
  if (args.size == 1 && args[0] == "%f") return emptyList()  // a buggy DE may fail to strip an unused parameter from a .desktop file

  @Suppress("SuspiciousPackagePrivateAccess")
  if (AppMode.HELP_OPTION in args) {
    println("""
        Some of the common commands and options (sorry, the full list is not yet supported):
          --help      prints a short list of commands and options
          --version   shows version information
          /project/dir
            opens a project from the given directory
          [/project/dir|--temp-project] [--wait] [--line <line>] [--column <column>] file
            opens the file, either in a context of the given project or as a temporary single-file project,
            optionally waiting until the editor tab is closed
          diff <left> <right>
            opens a diff window between <left> and <right> files/directories
          merge <local> <remote> [base] <merged>
            opens a merge window between <local> and <remote> files (with optional common <base>), saving the result to <merged>
        """.trimIndent())
    exitProcess(0)
  }

  @Suppress("SuspiciousPackagePrivateAccess")
  if (AppMode.VERSION_OPTION in args) {
    val appInfo = ApplicationInfoImpl.getShadowInstance()
    val edition = ApplicationNamesInfo.getInstance().editionName?.let { " (${it})" } ?: ""
    println("${appInfo.fullApplicationName}${edition}\nBuild #${appInfo.build.asString()}")
    exitProcess(0)
  }

  val (propertyArgs, otherArgs) = args.partition { it.startsWith("-D") && it.contains("=") }
  propertyArgs.forEach { arg ->
    val (option, value) = arg.removePrefix("-D").split("=", limit = 2)
    System.setProperty(option, value)
  }
  return otherArgs
}

@Suppress("HardCodedStringLiteral")
private fun installPluginUpdates() {
  try {
    // referencing `StartupActionScriptManager` is OK - a string constant will be inlined
    val scriptFile = Path.of(PathManager.getPluginTempPath(), StartupActionScriptManager.ACTION_SCRIPT_FILE)
    if (Files.isRegularFile(scriptFile)) {
      // load StartupActionScriptManager and all other related class (ObjectInputStream and so on loaded as part of class define)
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

// separate class for nicer presentation in dumps
private class StartupAbortedExceptionHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
  override fun handleException(context: CoroutineContext, exception: Throwable) {
    StartupAbortedException.processException(exception)
  }

  override fun toString() = "StartupAbortedExceptionHandler"
}
