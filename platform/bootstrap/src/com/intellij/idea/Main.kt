// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal
@file:JvmName("Main")
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
import com.intellij.openapi.project.impl.P3SupportInstaller
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.bootstrap.initMarketplace
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.bootstrap.AppStarter
import com.intellij.platform.ide.bootstrap.StartupErrorReporter
import com.intellij.platform.ide.bootstrap.startApplication
import com.intellij.platform.impl.toolkit.IdeFontManager
import com.intellij.platform.impl.toolkit.IdeGraphicsEnvironment
import com.intellij.platform.impl.toolkit.IdeToolkit
import com.intellij.util.ui.TextLayoutUtil
import com.jetbrains.JBR
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Toolkit
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
  mainImpl(
    rawArgs = rawArgs,
    startupTimings = startupTimings,
    startTimeUnixNano = startTimeUnixNano,
    changeClassPath = null,
  )
}

internal fun mainImpl(
  rawArgs: Array<String>,
  startupTimings: ArrayList<Any>,
  startTimeUnixNano: Long,
  changeClassPath: Consumer<ClassLoader>?,
) {
  val args = preprocessArgs(rawArgs)
  AppMode.setFlags(args)
  addBootstrapTiming("AppMode.setFlags", startupTimings)
  try {
    PathManager.loadProperties()
    addBootstrapTiming("properties loading", startupTimings)
    PathManager.customizePaths(args)
    addBootstrapTiming("customizePaths", startupTimings)
    P3SupportInstaller.seal()

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
  catch (t: Throwable) {
    StartupErrorReporter.showError(BootstrapBundle.message("bootstrap.error.title.start.failed"), t)
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
          return com.intellij.platform.diagnostic.telemetry.impl.span(name, context, action)
        }
      }
    }

    if (AppMode.isRemoteDevHost() || java.lang.Boolean.getBoolean("ide.started.from.remote.dev.launcher")) {
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

    startApplication(
      args = args,
      configImportNeededDeferred = configImportNeededDeferred,
      customTargetDirectoryToImportConfig = customTargetDirectoryToImportConfig,
      mainClassLoaderDeferred = mainClassLoaderDeferred,
      appStarterDeferred = appStarterDeferred,
      mainScope = mainScope,
      busyThread = busyThread,
    )
  }
}

/**
 * Directory where the configuration files should be imported to.
 * This property is used to override the default target directory ([PathManager.getConfigPath]) when a custom way to read and write
 * configuration files is implemented.
 */
@JvmField
internal var customTargetDirectoryToImportConfig: Path? = null

internal fun isConfigImportNeeded(configPath: Path): Boolean =
  !Files.exists(configPath) ||
  Files.exists(configPath.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME)) ||
  customTargetDirectoryToImportConfig != null

private fun initRemoteDev(args: List<String>) {
  if (!JBR.isGraphicsUtilsSupported()) {
    error("JBR version 17.0.6b796 or later is required to run a remote-dev server with lux")
  }

  val isSplitMode = args.firstOrNull() == AppMode.SPLIT_MODE_COMMAND
  if (isSplitMode) {
    System.setProperty("jb.privacy.policy.text", "<!--999.999-->")
    System.setProperty("jb.consents.confirmation.enabled", "false")
    System.setProperty("idea.initially.ask.config", "never")
  }

  // avoid an icon jumping in dock for the backend process
  if (SystemInfoRt.isMac) {
    val shouldInitDefaultToolkit = isSplitMode || isInAquaSession()
    if (System.getProperty("REMOTE_DEV_INIT_MAC_DEFAULT_TOOLKIT", shouldInitDefaultToolkit.toString()).toBoolean()) {
      // this makes sure that the following call doesn't create an icon in Dock
      System.setProperty("apple.awt.BackgroundOnly", "true")
      // this tells the OS that app initialization is finished
      Toolkit.getDefaultToolkit()
    }
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

private fun isInAquaSession(): Boolean {
  if (!SystemInfoRt.isMac) return false

  if ("true" == System.getenv("AWT_FORCE_HEADFUL")) {
    return false // the value is forcefully set, assume the worst case
  }

  try {
    val aClass = ClassLoader.getPlatformClassLoader().loadClass("sun.awt.PlatformGraphicsInfo")
    val handle = MethodHandles.lookup().findStatic(aClass, "isInAquaSession", MethodType.methodType(Boolean::class.javaPrimitiveType))
    return handle.invoke() as Boolean
  }
  catch (e: Throwable) {
    e.printStackTrace()
    return false
  }
}

private fun initLux() {
  // See also 'AWT_FORCE_HEADFUL'
  setStaticField(java.awt.GraphicsEnvironment::class.java, "headless", false) // ensure cached value is overridden
  System.setProperty("java.awt.headless", false.toString())

  System.setProperty("swing.volatileImageBufferEnabled", false.toString())
  System.setProperty("keymap.current.os.only", false.toString())
  System.setProperty("awt.nativeDoubleBuffering", false.toString())
  System.setProperty("swing.bufferPerWindow", true.toString())
  System.setProperty("swing.ignoreDoubleBufferingDisable", true.toString())
  // disables AntiFlickeringPanel that slows down Lux rendering,
  // see RDCT-1076 Debugger tree is rendered slowly under Lux
  System.setProperty("debugger.anti.flickering.delay", 0.toString())

  setStaticField(Toolkit::class.java, "toolkit", IdeToolkit())
  System.setProperty("awt.toolkit", IdeToolkit::class.java.canonicalName)

  @Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
  setStaticField(sun.font.FontManagerFactory::class.java, "instance", IdeFontManager())
  @Suppress("SpellCheckingInspection")
  System.setProperty("sun.font.fontmanager", IdeFontManager::class.java.canonicalName)

  TextLayoutUtil.disableLayoutInTextComponents()
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

private fun installPluginUpdates() {
  try {
    // load `StartupActionScriptManager` and other related classes (`ObjectInputStream`, etc.) only when there is a script to run
    // (referencing a string constant is OK - it is inlined by the compiler)
    val scriptFile = PathManager.getStartupScriptDir().resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE)
    if (Files.isRegularFile(scriptFile)) {
      StartupActionScriptManager.executeActionScript()
    }
  }
  catch (t: Throwable) {
    StartupErrorReporter.pluginInstallationProblem(t)
  }
}

// separate class for nicer presentation in dumps
private class StartupAbortedExceptionHandler : AbstractCoroutineContextElement(CoroutineExceptionHandler), CoroutineExceptionHandler {
  override fun handleException(context: CoroutineContext, exception: Throwable) {
    StartupErrorReporter.processException(exception)
  }

  override fun toString() = "StartupAbortedExceptionHandler"
}
