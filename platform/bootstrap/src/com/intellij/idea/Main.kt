// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Main")
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.idea

import com.intellij.concurrency.IdeaForkJoinWorkerThreadFactory
import com.intellij.diagnostic.CoroutineTracerShim
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.platform.bootstrap.initMarketplace
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.AppStarter
import com.intellij.platform.ide.bootstrap.StartupErrorReporter
import com.intellij.platform.ide.bootstrap.startApplication
import com.intellij.platform.impl.toolkit.IdeFontManager
import com.intellij.platform.impl.toolkit.IdeGraphicsEnvironment
import com.intellij.platform.impl.toolkit.IdeToolkit
import com.jetbrains.JBR
import kotlinx.coroutines.*
import sun.font.FontManagerFactory
import java.awt.Toolkit
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.Consumer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

fun main(rawArgs: Array<String>) {
  val startupTimings = ArrayList<Any>(12)
  val startTimeNano = System.nanoTime()
  val startTimeUnixNano = System.currentTimeMillis() * 1000000
  startupTimings.add("startup begin")
  startupTimings.add(startTimeNano)
  mainImpl(rawArgs = rawArgs, startupTimings = startupTimings, startTimeUnixNano = startTimeUnixNano)
}

internal fun mainImpl(rawArgs: Array<String>,
                      startupTimings: ArrayList<Any>,
                      startTimeUnixNano: Long,
                      changeClassPath: Consumer<ClassLoader>? = null) {
  val args = preprocessArgs(rawArgs)
  AppMode.setFlags(args)
  addBootstrapTiming("AppMode.setFlags", startupTimings)
  try {
    PathManager.loadProperties()
    addBootstrapTiming("properties loading", startupTimings)
    PathManager.customizePaths()
    addBootstrapTiming("customizePaths", startupTimings)

    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      addBootstrapTiming("main scope creating", startupTimings)

      val busyThread = Thread.currentThread()
      withContext(Dispatchers.Default + StartupAbortedExceptionHandler() + rootTask()) {
        addBootstrapTiming("init scope creating", startupTimings)
        StartUpMeasurer.addTimings(startupTimings, "bootstrap", startTimeUnixNano)
        startApp(args = args, mainScope = this@runBlocking, busyThread = busyThread, changeClassPath = changeClassPath)
      }

      awaitCancellation()
    }
  }
  catch (e: Throwable) {
    StartupErrorReporter.showMessage(BootstrapBundle.message("bootstrap.error.title.start.failed"), e)
    exitProcess(AppExitCodes.STARTUP_EXCEPTION)
  }
}

private suspend fun startApp(args: List<String>, mainScope: CoroutineScope, busyThread: Thread, changeClassPath: Consumer<ClassLoader>?) {
  span("startApplication") {
    launch {
      CoroutineTracerShim.coroutineTracer = object : CoroutineTracerShim {
        override suspend fun getTraceActivity() = com.intellij.platform.diagnostic.telemetry.impl.getTraceActivity()
        override fun rootTrace() = rootTask()

        override suspend fun <T> span(name: String, context: CoroutineContext, action: suspend CoroutineScope.() -> T): T {
          return com.intellij.platform.diagnostic.telemetry.impl.span(name = name, context = context, action = action)
        }
      }
    }

    if (AppMode.isRemoteDevHost()) {
      span("cwm host init") {
        initRemoteDev(args)
      }
    }

    launch(CoroutineName("ForkJoin CommonPool configuration")) {
      IdeaForkJoinWorkerThreadFactory.setupForkJoinCommonPool(AppMode.isHeadless())
    }

    // some code can rely on this flag, even if it is not used to show config import dialog or something config related,
    // that's why we check it even in a headless mode (https://youtrack.jetbrains.com/issue/IJPL-333)
    val configImportNeededDeferred = async(Dispatchers.IO) {
      isConfigImportNeeded(PathManager.getConfigDir())
    }

    // this check must be performed before system directories are locked
    if (!AppMode.isCommandLine() || java.lang.Boolean.getBoolean(AppMode.FORCE_PLUGIN_UPDATES)) {
      span("plugin updates installation") {
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
    }

    // must be after installPluginUpdates
    span("marketplace init") {
      // 'marketplace' plugin breaks JetBrains Client, so for now this condition is used to disable it
      if (changeClassPath == null) {  
        initMarketplace()
      }
    }

    // must be after initMarketplace because initMarketplace can affect the main class loading (byte code transformer)
    val appStarterDeferred: Deferred<AppStarter>
    val mainClassLoaderDeferred: Deferred<ClassLoader>?
    if (changeClassPath == null) {
      appStarterDeferred = async(CoroutineName("main class loading")) {
        val aClass = AppMode::class.java.classLoader.loadClass("com.intellij.idea.MainImpl")
        MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as AppStarter
      }
      mainClassLoaderDeferred = null
    }
    else {
      mainClassLoaderDeferred = async(CoroutineName("main class loader initializing")) {
        val classLoader = AppMode::class.java.classLoader
        changeClassPath.accept(classLoader)
        classLoader
      }

      appStarterDeferred = async(CoroutineName("main class loading")) {
        val aClass = mainClassLoaderDeferred.await().loadClass("com.intellij.idea.MainImpl")
        MethodHandles.lookup().findConstructor(aClass, MethodType.methodType(Void.TYPE)).invoke() as AppStarter
      }
    }

    startApplication(args = args,
                     configImportNeededDeferred = configImportNeededDeferred,
                     targetDirectoryToImportConfig = customTargetDirectoryToImportConfig,
                     mainClassLoaderDeferred = mainClassLoaderDeferred,
                     appStarterDeferred = appStarterDeferred,
                     mainScope = mainScope,
                     busyThread = busyThread)
  }
}

/**
 * Directory where the configuration files should be imported to.
 * This property is used to override the default target directory ([PathManager.getConfigPath]) when a custom way to read and write
 * configuration files is implemented.
 */
@JvmField
internal var customTargetDirectoryToImportConfig: Path? = null

internal fun isConfigImportNeeded(configPath: Path): Boolean {
  return ConfigImportHelper.isConfigImportExpected(configPath) || customTargetDirectoryToImportConfig != null
}

private fun initRemoteDev(args: List<String>) {
  if (!JBR.isGraphicsUtilsSupported()) {
    error("JBR version 17.0.6b796 or later is required to run a remote-dev server with lux")
  }

  if (args.firstOrNull() == AppMode.SPLIT_MODE_COMMAND) {
    System.setProperty("idea.initially.ask.config", "never")
  }
  initRemoteDevGraphicsEnvironment()
  initLux()
}

private fun initRemoteDevGraphicsEnvironment() {
  JBR.getProjectorUtils().setLocalGraphicsEnvironmentProvider { IdeGraphicsEnvironment.instance }
}

private fun setStaticField(clazz: Class<out Any>, fieldName: String, value: Any) {
  val lookup = MethodHandles.lookup()

  val field = clazz.getDeclaredField(fieldName)
  field.isAccessible = true
  val handle = lookup.unreflectSetter(field)
  handle.invoke(value)
}

private fun initLux() {
  System.setProperty("java.awt.headless", false.toString())
  System.setProperty("swing.volatileImageBufferEnabled", false.toString())
  System.setProperty("keymap.current.os.only", false.toString())
  System.setProperty("awt.nativeDoubleBuffering", false.toString())
  System.setProperty("swing.bufferPerWindow", true.toString())

  setStaticField(Toolkit::class.java, "toolkit", IdeToolkit())
  System.setProperty("awt.toolkit", IdeToolkit::class.java.canonicalName)

  setStaticField(FontManagerFactory::class.java, "instance", IdeFontManager())
  @Suppress("SpellCheckingInspection")
  System.setProperty("sun.font.fontmanager", IdeFontManager::class.java.canonicalName)
}

private fun addBootstrapTiming(name: String, startupTimings: MutableList<Any>) {
  startupTimings.add(name)
  startupTimings.add(System.nanoTime())
}

private fun preprocessArgs(args: Array<String>): List<String> {
  if (args.isEmpty()) {
    return Collections.emptyList()
  }

  // a buggy DE may fail to strip an unused parameter from a .desktop file
  if (args.size == 1 && args[0] == "%f") {
    return Collections.emptyList()
  }

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

  val (propertyArgs, otherArgs) = args.partition { it.startsWith("-D") && it.contains('=') }
  propertyArgs.forEach { arg ->
    val (option, value) = arg.removePrefix("-D").split('=', limit = 2)
    System.setProperty(option, value)
  }
  return otherArgs
}

@Suppress("HardCodedStringLiteral")
private fun installPluginUpdates() {
  try {
    // referencing `StartupActionScriptManager` is OK - a string constant will be inlined
    val scriptFile = PathManager.getStartupScriptDir().resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE)
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
    StartupErrorReporter.processException(exception)
  }

  override fun toString() = "StartupAbortedExceptionHandler"
}