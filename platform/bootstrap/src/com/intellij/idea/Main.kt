// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Main")
@file:Suppress("ReplacePutWithAssignment")
package com.intellij.idea

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.*
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.plugins.StartupAbortedException
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.PathManager
import kotlinx.coroutines.*
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
fun main(rawArgs: Array<String>) {
  val startupTimings = LinkedHashMap<String, Long>(6)
  startupTimings.put("startup begin", System.nanoTime())

  val args: List<String> = if (rawArgs.size == 1 && rawArgs[0] == "%f") emptyList() else Arrays.asList(*rawArgs)
  AppMode.setFlags(args)
  try {
    bootstrap(startupTimings)
    startupTimings.put("main scope creating", System.nanoTime())
    runBlocking(rootTask()) {
      StartUpMeasurer.addTimings(startupTimings, "bootstrap")
      val appInitPreparationActivity = StartUpMeasurer.startActivity("app initialization preparation")
      val busyThread = Thread.currentThread()

      launch(CoroutineName("ForkJoin CommonPool configuration") + Dispatchers.Default) {
        IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(AppMode.isHeadless())
      }

      // not IO-, but CPU-bound due to descrambling, don't use here IO dispatcher
      val appStarterDeferred = async(CoroutineName("main class loading") + Dispatchers.Default) {
        val aClass = AppStarter::class.java.classLoader.loadClass("com.intellij.idea.MainImpl")
        MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as AppStarter
      }

      initProjectorIfNeeded(args)

      withContext(Dispatchers.Default + StartupAbortedExceptionHandler()) {
        StartUpMeasurer.appInitPreparationActivity = appInitPreparationActivity
        val mainScope = this@runBlocking
        startApplication(args = args,
                         appStarterDeferred = appStarterDeferred,
                         mainScope = mainScope,
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
  if (args.isEmpty() || (AppMode.CWM_HOST_COMMAND != args[0] && AppMode.CWM_HOST_NO_LOBBY_COMMAND != args[0])) {
    return
  }

  runActivity("cwm host init") {
    val projectorMainClass = AppStarter::class.java.classLoader.loadClass("org.jetbrains.projector.server.ProjectorLauncher\$Starter")
    MethodHandles.privateLookupIn(projectorMainClass, MethodHandles.lookup()).findStatic(
      projectorMainClass, "runProjectorServer", MethodType.methodType(Boolean::class.javaPrimitiveType)
    ).invoke()
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

// separate class for nicer presentation in dumps
private class StartupAbortedExceptionHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
  override fun handleException(context: CoroutineContext, exception: Throwable) {
    StartupAbortedException.processException(exception)
  }

  override fun toString() = "StartupAbortedExceptionHandler"
}