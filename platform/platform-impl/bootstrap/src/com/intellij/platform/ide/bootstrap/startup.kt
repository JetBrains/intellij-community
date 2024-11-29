// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StartupUtil")
package com.intellij.platform.ide.bootstrap

import com.intellij.BundleBase
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.BootstrapBundle
import com.intellij.ide.CliResult
import com.intellij.ide.IdeBundle
import com.intellij.ide.bootstrap.InitAppContext
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.AppExitCodes
import com.intellij.idea.AppMode
import com.intellij.idea.LoggerFactory
import com.intellij.jna.JnaLoader
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.mac.initMacApplication
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.svg.SvgCacheManager
import com.intellij.util.EnvironmentUtil
import com.intellij.util.Java11Shim
import com.intellij.util.lang.ZipFilePool
import com.jetbrains.JBR
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Toolkit
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.system.exitProcess

internal const val IDE_STARTED: String = "------------------------------------------------------ IDE STARTED ------------------------------------------------------"
private const val IDE_SHUTDOWN = "------------------------------------------------------ IDE SHUTDOWN ------------------------------------------------------"

private const val IDEA_CLASS_BEFORE_APPLICATION_PROPERTY = "idea.class.before.app"
private const val MAGIC_MAC_PATH = "/AppTranslocation/"

private val commandProcessor: AtomicReference<(List<String>) -> Deferred<CliResult>> = AtomicReference {
  CompletableDeferred(CliResult(AppExitCodes.ACTIVATE_NOT_INITIALIZED, IdeBundle.message("activation.not.initialized")))
}

// checked - using a Deferred type doesn't lead to loading this class on StartupUtil init
internal var shellEnvDeferred: Deferred<Boolean?>? = null
  private set

@Volatile
@JvmField
internal var isInitialStart: CompletableDeferred<Boolean>? = null

// the main thread's dispatcher is sequential - use it with care
@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.startApplication(
  args: List<String>,
  configImportNeededDeferred: Deferred<Boolean>,
  customTargetDirectoryToImportConfig: Path?,
  mainClassLoaderDeferred: Deferred<ClassLoader>?,
  appStarterDeferred: Deferred<AppStarter>,
  mainScope: CoroutineScope,
  busyThread: Thread,
) {
  launch {
    Java11Shim.INSTANCE = Java11ShimImpl()
  }

  val appInfoDeferred = async {
    mainClassLoaderDeferred?.await()
    coroutineScope {
      // required for log essential info about IDE, Wayland app id
      async(CoroutineName("app name info")) {
        ApplicationNamesInfo.getInstance()
      }

      span("app info") {
        // required for DisabledPluginsState and EUA
        ApplicationInfoImpl.getShadowInstance()
      }
    }
  }

  val isHeadless = AppMode.isHeadless()

  val lockSystemDirsJob = launch {
    // the "import-needed" check must be performed strictly before IDE directories are locked
    configImportNeededDeferred.join()
    span("system dirs locking") {
      lockSystemDirs(args)
    }
  }

  val consoleLoggerJob = configureJavaUtilLogging()

  launch {
    LoadingState.setStrictMode()
    LoadingState.errorHandler = BiConsumer { message, throwable ->
      logger<LoadingState>().error(message, throwable)
    }
  }

  val initAwtToolkitJob = scheduleInitAwtToolkit(lockSystemDirsJob, busyThread)
  val initBaseLafJob = launch {
    initUi(initAwtToolkitJob = initAwtToolkitJob, isHeadless = isHeadless, asyncScope = this@startApplication)
  }

  var initUiScale: Job? = null
  if (!isHeadless) {
    initUiScale = launch {
      if (SystemInfoRt.isMac) {
        initAwtToolkitJob.join()
        JBUIScale.preloadOnMac()
      }
      else {
        // A splash instance must not be created before base LaF is created.
        // It is important on Linux, where GTK LaF must be initialized (to properly set up the scale factor).
        // https://youtrack.jetbrains.com/issue/IDEA-286544
        initBaseLafJob.join()
      }
    }

    scheduleUpdateFrameClassAndWindowIconAndPreloadSystemFonts(initAwtToolkitJob = initAwtToolkitJob, initUiScale = initUiScale, appInfoDeferred = appInfoDeferred)
    scheduleShowSplashIfNeeded(lockSystemDirsJob = lockSystemDirsJob, initUiScale = initUiScale, appInfoDeferred = appInfoDeferred, args = args)
  }

  val initLafJob = launch {
    initUiScale?.join()
    initBaseLafJob.join()
    if (!isHeadless) {
      configureCssUiDefaults()
    }
  }

  val zipFilePoolDeferred = async {
    val result = ZipFilePoolImpl()
    ZipFilePool.POOL = result
    result
  }

  launch {
    initLafJob.join()

    if (!isHeadless) {
      // preload native lib
      JBR.getWindowDecorations()
      if (SystemInfoRt.isMac) {
        Menu.isJbScreenMenuEnabled()
      }
    }
  }

  // system dirs checking must happen after locking system dirs
  val checkSystemDirJob = checkSystemDirs(lockSystemDirsJob)

  // log initialization must happen only after locking the system directory
  val logDeferred = setupLogger(consoleLoggerJob, checkSystemDirJob)
  if (!isHeadless) {
    launch {
      lockSystemDirsJob.join()

      span("SvgCache creation") {
        SvgCacheManager.svgCache = SvgCacheManager.createSvgCacheManager()
      }
    }
  }

  shellEnvDeferred = async {
    // EnvironmentUtil wants logger
    logDeferred.join()
    span("environment loading", Dispatchers.IO) {
      EnvironmentUtil.loadEnvironment(coroutineContext.job)
    }
  }

  scheduleLoadSystemLibsAndLogInfoAndInitMacApp(logDeferred = logDeferred, appInfoDeferred = appInfoDeferred, initUiDeferred = initLafJob, args = args, mainScope = mainScope)

  val euaDocumentDeferred = async { loadEuaDocument(appInfoDeferred) }

  val configImportDeferred: Deferred<Job?> = async {
    importConfigIfNeeded(
      isHeadless = isHeadless,
      configImportNeededDeferred = configImportNeededDeferred,
      lockSystemDirsJob = lockSystemDirsJob,
      logDeferred = logDeferred,
      args = args,
      customTargetDirectoryToImportConfig = customTargetDirectoryToImportConfig,
      appStarterDeferred = appStarterDeferred,
      euaDocumentDeferred = euaDocumentDeferred,
      initLafJob = initLafJob,
    )
  }

  val pluginSetDeferred = async {
    // plugins cannot be loaded when a config import is necessary, because plugins may be added after importing
    configImportDeferred.join()

    if (!PluginAutoUpdater.shouldSkipAutoUpdate()) {
      span("plugin auto update") {
          PluginAutoUpdater.applyPluginUpdates(logDeferred)
      }
    }

    PluginManagerCore.scheduleDescriptorLoading(
      coroutineScope = this@startApplication,
      zipFilePoolDeferred = zipFilePoolDeferred,
      mainClassLoaderDeferred = mainClassLoaderDeferred,
      logDeferred = logDeferred,
    )
  }

  val isInternal = java.lang.Boolean.getBoolean(ApplicationManagerEx.IS_INTERNAL_PROPERTY)
  if (isInternal) {
    launch(CoroutineName("assert on missed keys enabling")) {
      BundleBase.assertOnMissedKeys(true)
    }
  }
  launch(CoroutineName("disposer debug mode enabling if needed")) {
    if (isInternal || Disposer.isDebugDisposerOn()) {
      Disposer.setDebugMode(true)
    }
  }

  val appRegisteredJob = CompletableDeferred<Unit>()

  val appLoaded = async {
    val initEventQueueJob = scheduleInitIdeEventQueue(initAwtToolkit = initAwtToolkitJob, isHeadless = isHeadless)

    checkSystemDirJob.join()

    val classBeforeAppProperty = System.getProperty(IDEA_CLASS_BEFORE_APPLICATION_PROPERTY)
    if (classBeforeAppProperty != null && !configImportNeededDeferred.await()) {
      logDeferred.join()
      runPreAppClass(args = args, classBeforeAppProperty = classBeforeAppProperty)
    }

    val app = span("app instantiation") {
      // we don't want to inherit mainScope Dispatcher and CoroutineTimeMeasurer, we only want the job
      @Suppress("SSBasedInspection")
      ApplicationImpl(CoroutineScope(mainScope.coroutineContext.job).childScope("Application"), isInternal)
    }

    loadApp(
      app = app,
      initAwtToolkitAndEventQueueJob = initEventQueueJob,
      pluginSetDeferred = pluginSetDeferred,
      appInfoDeferred = appInfoDeferred,
      euaDocumentDeferred = euaDocumentDeferred,
      asyncScope = this@startApplication,
      initLafJob = initLafJob,
      logDeferred = logDeferred,
      appRegisteredJob = appRegisteredJob,
      args = args.filterNot { CommandLineArgs.isKnownArgument(it) },
    )
  }

  launch {
    // required for appStarter.prepareStart
    appInfoDeferred.join()

    val appStarter = span("main class loading waiting") {
      appStarterDeferred.await()
    }

    withContext(mainScope.coroutineContext + CoroutineName("appStarter set")) {
      appStarter.prepareStart(args)
    }

    // must be scheduled before starting app
    pluginSetDeferred.join()

    // with the main dispatcher for non-technical reasons
    mainScope.launch {
      appStarter.start(InitAppContext(appRegistered = appRegisteredJob, appLoaded = appLoaded))
    }
  }

  // out of appLoaded scope
  launch {
    // starter is used later, but we need to wait for appLoaded completion
    val starter = appLoaded.await()

    val isInitialStart = configImportDeferred.await()
    // appLoaded not only provides starter but also loads app, that's why it is here
    launch {
      if (ConfigImportHelper.isFirstSession()) {
        IdeStartupWizardCollector.logWizardExperimentState()
      }
    }

    if (isInitialStart != null) {
      LoadingState.compareAndSetCurrentState(LoadingState.COMPONENTS_LOADED, LoadingState.APP_READY)
      val log = logDeferred.await()
      runCatching {
        span("startup wizard run") {
          runStartupWizard(isInitialStart = isInitialStart, app = ApplicationManager.getApplication())
        }
      }.getOrLogException(log)
    }

    executeApplicationStarter(starter = starter, args = args)
  }
}

private fun CoroutineScope.scheduleLoadSystemLibsAndLogInfoAndInitMacApp(
  logDeferred: Deferred<Logger>,
  appInfoDeferred: Deferred<ApplicationInfoEx>,
  initUiDeferred: Job,
  args: List<String>,
  mainScope: CoroutineScope,
) {
  launch {
    if (SystemInfoRt.isWindows) {
      span("system libs setup") {
        if (System.getProperty("winp.folder.preferred") == null) {
          System.setProperty("winp.folder.preferred", PathManager.getTempPath())
        }
      }
    }

    // this must happen after locking system dirs
    val log = logDeferred.await()

    span("system libs loading", Dispatchers.IO) {
      JnaLoader.load(log)
    }

    val appInfo = appInfoDeferred.await()
    launch(CoroutineName("essential IDE info logging")) {
      logEssentialInfoAboutIde(log = log, appInfo = appInfo, args = args)
    }

    if (SystemInfoRt.isMac && !AppMode.isHeadless() && !AppMode.isRemoteDevHost()) {
      // JNA and Swing are used - invoke only after both are loaded
      initUiDeferred.join()
      launch(CoroutineName("mac app init")) {
        runCatching {
          initMacApplication(mainScope)
        }.getOrLogException(log)
      }
    }
  }
}

@Internal
// called by the app after startup
fun setActivationListener(processor: (List<String>) -> Deferred<CliResult>) {
  commandProcessor.set(processor)
}

private suspend fun runPreAppClass(args: List<String>, classBeforeAppProperty: String) {
  span("pre app class running") {
    try {
      val aClass = AppStarter::class.java.classLoader.loadClass(classBeforeAppProperty)
      MethodHandles.lookup()
        .findStatic(aClass, "invoke", MethodType.methodType(Void.TYPE, Array<String>::class.java))
        .invoke(args.toTypedArray())
    }
    catch (e: Exception) {
      logger<AppStarter>().error("Failed pre-app class init for class $classBeforeAppProperty", e)
    }
  }
}

private fun CoroutineScope.configureJavaUtilLogging(): Job {
  return launch(CoroutineName("console logger configuration")) {
    val rootLogger = java.util.logging.Logger.getLogger("")
    if (rootLogger.handlers.isEmpty()) {
      rootLogger.level = Level.WARNING
      val consoleHandler = ConsoleHandler()
      consoleHandler.level = Level.WARNING
      rootLogger.addHandler(consoleHandler)
    }
  }
}

private fun CoroutineScope.checkSystemDirs(lockSystemDirJob: Job): Job {
  return launch {
    lockSystemDirJob.join()

    val homePath = PathManager.getHomePath()
    val configPath = PathManager.getConfigDir()
    val systemPath = PathManager.getSystemDir()
    if (!span("system dirs checking") { doCheckSystemDirs(homePath, configPath, systemPath) }) {
      exitProcess(AppExitCodes.DIR_CHECK_FAILED)
    }
  }
}

private suspend fun doCheckSystemDirs(homePath: String, configPath: Path, systemPath: Path): Boolean {
  if (configPath == systemPath) {
    StartupErrorReporter.showError(
      BootstrapBundle.message("bootstrap.error.title.settings"),
      BootstrapBundle.message("bootstrap.error.message.same.paths", PathManager.PROPERTY_CONFIG_PATH, PathManager.PROPERTY_SYSTEM_PATH)
    )
    return false
  }

  if (SystemInfoRt.isMac && homePath.contains(MAGIC_MAC_PATH)) {
    StartupErrorReporter.showError(
      BootstrapBundle.message("bootstrap.error.title.settings"),
      BootstrapBundle.message("bootstrap.error.message.mac.trans")
    )
    return false
  }

  return withContext(Dispatchers.IO) {
    val logPath = Path.of(PathManager.getLogPath()).normalize()
    val tempPath = Path.of(PathManager.getTempPath()).normalize()
    // directories might be nested, hence should be checked sequentially
    checkDirectory(configPath, kind = 0, property = PathManager.PROPERTY_CONFIG_PATH) &&
    checkDirectory(systemPath, kind = 1, property = PathManager.PROPERTY_SYSTEM_PATH) &&
    checkDirectory(logPath, kind = 2, property = PathManager.PROPERTY_LOG_PATH) &&
    checkDirectory(tempPath, kind = 3, property = PathManager.PROPERTY_SYSTEM_PATH)
  }
}

private fun checkDirectory(directory: Path, kind: Int, property: String): Boolean {
  try {
    Files.createDirectories(directory)
  }
  catch (e: Exception) {
    val title = BootstrapBundle.message("bootstrap.error.title.invalid.directory", kind)
    val problem = BootstrapBundle.message("bootstrap.error.problem.dir")
    val message = BootstrapBundle.message("bootstrap.error.message.dir.problem", problem, property, directory, e.javaClass.name, e.message)
    StartupErrorReporter.showError(title, message)
    return false
  }

  val tempFile = directory.resolve("ij${Random().nextInt(Int.MAX_VALUE)}.tmp")
  try {
    Files.writeString(tempFile, "-", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
  }
  catch (e: Exception) {
    val title = BootstrapBundle.message("bootstrap.error.title.invalid.directory", kind)
    val problem = BootstrapBundle.message("bootstrap.error.problem.file")
    val message = BootstrapBundle.message("bootstrap.error.message.dir.problem", problem, property, directory, e.javaClass.name, e.message)
    StartupErrorReporter.showError(title, message)
    return false
  }
  finally {
    try {
      Files.deleteIfExists(tempFile)
    }
    catch (_: Exception) { }
  }

  return true
}

private suspend fun lockSystemDirs(args: List<String>) {
  val directoryLock = DirectoryLock(PathManager.getConfigDir(), PathManager.getSystemDir()) { processorArgs ->
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      try {
        commandProcessor.get().invoke(processorArgs).await()
      }
      catch (t: Throwable) {
        @Suppress("SSBasedInspection")
        Logger.getInstance("#com.intellij.platform.ide.bootstrap.StartupUtil").error(t)
        CliResult(AppExitCodes.ACTIVATE_ERROR, IdeBundle.message("activation.unknown.error", t.message))
      }
    }
  }

  try {
    val currentDir = Path.of("").toAbsolutePath().normalize()
    val result = withContext(Dispatchers.IO) { directoryLock.lockOrActivate(currentDir, args) }
    if (result == null) {
      ShutDownTracker.getInstance().registerShutdownTask {
        try {
          directoryLock.dispose()
        }
        catch (e: Throwable) {
          logger<DirectoryLock>().error(e)
        }
      }
    }
    else {
      result.message?.let { println(it) }
      exitProcess(result.exitCode)
    }
  }
  catch (e: DirectoryLock.CannotActivateException) {
    if (args.isEmpty()) {
      StartupErrorReporter.showError(BootstrapBundle.message("bootstrap.error.title.start.failed"), e.message)
    }
    else {
      println(e.message)
    }
    exitProcess(AppExitCodes.INSTANCE_CHECK_FAILED)
  }
  catch (e: Throwable) {
    StartupErrorReporter.showError(BootstrapBundle.message("bootstrap.error.title.start.failed"), e)
    exitProcess(AppExitCodes.STARTUP_EXCEPTION)
  }
}

private fun CoroutineScope.setupLogger(consoleLoggerJob: Job, checkSystemDirJob: Job): Deferred<Logger> {
  return async {
    consoleLoggerJob.join()
    checkSystemDirJob.join()

    span("file logger configuration") {
      try {
        Logger.setFactory(LoggerFactory())
      }
      catch (e: Exception) {
        e.printStackTrace()
      }

      val log = logger<AppStarter>()
      log.info(IDE_STARTED)
      ShutDownTracker.getInstance().registerShutdownTask { log.info(IDE_SHUTDOWN) }
      if (java.lang.Boolean.parseBoolean(System.getProperty("intellij.log.stdout", "true"))) {
        System.setOut(PrintStreamLogger("STDOUT", System.out))
        System.setErr(PrintStreamLogger("STDERR", System.err))
      }
      log
    }
  }
}

fun logEssentialInfoAboutIde(log: Logger, appInfo: ApplicationInfo, args: List<String>) {
  val buildTimeString = DateTimeFormatter.RFC_1123_DATE_TIME.format(appInfo.buildTime)
  log.info("IDE: ${ApplicationNamesInfo.getInstance().fullProductName} (build #${appInfo.build.asString()}, $buildTimeString)")
  log.info("OS: ${SystemInfoRt.OS_NAME} (${SystemInfoRt.OS_VERSION})")
  log.info("JRE: ${System.getProperty("java.runtime.version", "-")}, ${System.getProperty("os.arch")} (${System.getProperty("java.vendor", "-")})")
  log.info("JVM: ${System.getProperty("java.vm.version", "-")} (${System.getProperty("java.vm.name", "-")})")
  log.info("PID: ${ProcessHandle.current().pid()}")
  if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
    log.info("desktop: ${System.getenv("XDG_CURRENT_DESKTOP")}")
    log.info("toolkit: ${Toolkit.getDefaultToolkit().javaClass.name}")
  }

  try {
    ManagementFactory.getRuntimeMXBean().inputArguments?.let { log.info("JVM options: ${it}") }
  }
  catch (e: Exception) {
    log.error("Failed to get JVM options", e)
  }

  log.info("args: ${args.joinToString(separator = " ")}")
  log.info("library path: ${System.getProperty("java.library.path")}")
  log.info("boot library path: ${System.getProperty("sun.boot.library.path")}")
  logEnvVar(log, "_JAVA_OPTIONS")
  logEnvVar(log, "JDK_JAVA_OPTIONS")
  logEnvVar(log, "JAVA_TOOL_OPTIONS")
  @Suppress("SystemGetProperty")
  log.info(
    """locale=${Locale.getDefault()} JNU=${System.getProperty("sun.jnu.encoding")} file.encoding=${System.getProperty("file.encoding")}
    ${PathManager.PROPERTY_CONFIG_PATH}=${logPath(PathManager.getConfigPath())}
    ${PathManager.PROPERTY_SYSTEM_PATH}=${logPath(PathManager.getSystemPath())}
    ${PathManager.PROPERTY_PLUGINS_PATH}=${logPath(PathManager.getPluginsPath())}
    ${PathManager.PROPERTY_LOG_PATH}=${logPath(PathManager.getLogPath())}""")
  val cores = Runtime.getRuntime().availableProcessors()
  val pool = ForkJoinPool.commonPool()
  log.info("CPU cores: $cores; ForkJoinPool.commonPool: $pool; factory: ${pool.factory}")
}

private fun logEnvVar(log: Logger, variable: String) {
  System.getenv(variable)?.let {
    log.info("${variable}=${it}")
  }
}

private fun logPath(path: String): String {
  try {
    val configured = Path.of(path)
    val real = configured.toRealPath()
    return if (configured == real) path else "$path -> $real"
  }
  catch (e: Exception) {
    return "$path -> ${e.javaClass.name}: ${e.message}"
  }
}

interface AppStarter {
  fun prepareStart(args: List<String>) {}

  suspend fun start(context: InitAppContext)

  /* called from IDE init thread */
  fun beforeImportConfigs() {}

  /* called from IDE init thread */
  fun importFinished(newConfigDir: Path) {}
}
