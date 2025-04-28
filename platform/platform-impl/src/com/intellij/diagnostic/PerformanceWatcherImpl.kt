// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.diagnostic

import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.DebugAttachDetector
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.sanitizeFileName
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import sun.awt.ModalityEvent
import sun.awt.ModalityListener
import sun.awt.SunToolkit
import java.awt.Toolkit
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.io.path.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val LOG: Logger
  get() = logger<PerformanceWatcherImpl>()

private const val TOLERABLE_LATENCY = 100L
private const val THREAD_DUMPS_PREFIX = "threadDumps-"
private const val DURATION_FILE_NAME = ".duration"
private const val PID_FILE_NAME = ".pid"
private val ideStartTime = ZonedDateTime.now()

private val EP_NAME = ExtensionPointName<PerformanceListener>("com.intellij.idePerformanceListener")

internal class PerformanceWatcherImpl(private val coroutineScope: CoroutineScope) : PerformanceWatcher() {
  private val logDir = PathManager.getLogDir()

  @Volatile
  private var swingApdex = ApdexData.EMPTY

  @Volatile
  private var generalApdex = ApdexData.EMPTY

  @Volatile
  private var lastSampling = System.nanoTime()
  private var currentEdtEventChecker: FreezeCheckerTask? = null
  private val jitWatcher = JitWatcher()
  private val unresponsiveIntervalLazy by lazy {
    RegistryManager.getInstance().get("performance.watcher.unresponsive.interval.ms")
  }

  private val isActive: Boolean = !ApplicationManager.getApplication().isHeadlessEnvironment

  private val taskFlow = MutableSharedFlow<FreezeCheckerTask?>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    if (isActive) {
      LOG.debug("Freeze detection started")
      coroutineScope.launch(CoroutineName("EDT freeze detector")) {
        asyncInit()

        taskFlow.collectLatest { task ->
          if (task == null) {
            return@collectLatest
          }

          delay(unresponsiveInterval.toLong())
          task.edtFrozen()
        }
      }
    }
    (Toolkit.getDefaultToolkit() as? SunToolkit)?.addModalityListener(object : ModalityListener {
      override fun modalityPushed(ev: ModalityEvent) { }

      override fun modalityPopped(ev: ModalityEvent) {
        stopCurrentTaskAndReEmit(FreezeCheckerTask(System.nanoTime()))
      }
    })
  }

  override fun startEdtSampling() {
    if (!isActive) {
      return
    }

    LOG.debug("EDT sampling started")
    coroutineScope.launch(CoroutineName("EDT sampling")) {
      try {
        val samplingIntervalMs = samplingInterval
        @Suppress("KotlinConstantConditions")
        if (samplingIntervalMs <= 0) {
          return@launch
        }

        while (true) {
          delay(samplingIntervalMs)
          samplePerformance(samplingIntervalMs)
        }
      }
      finally {
        LOG.debug("EDT sampling stopped")
      }
    }
  }

  private suspend fun asyncInit() {
    runCatching {
      reportCrashesIfAny()
    }.getOrLogException(LOG)

    withContext(Dispatchers.IO) {
      cleanOldFiles(logDir, 0)
    }

    if (ApplicationInfoImpl.getShadowInstance().isEAP) {
      coroutineScope.launch {
        val reasonableThreadPoolSize = ApplicationManager.getApplication().serviceAsync<RegistryManager>()
          .get("reasonable.application.thread.pool.size")
        val service = AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService
        val allAvailableProcessors = Runtime.getRuntime().availableProcessors()
        service.setNewThreadListener { _, _ ->
          val executorSize = service.backendPoolExecutorSize
          if (executorSize > reasonableThreadPoolSize.asInteger() + allAvailableProcessors) {
            val message = "Too many threads: $executorSize created in the global Application pool. " +
                          "($reasonableThreadPoolSize, available processors: $allAvailableProcessors)"
            val file = dumpThreads(pathPrefix = "newPooledThread/", appendMillisecondsToFileName = true, contentsPrefix = message, stripDump = true)
            LOG.info(message + if (file == null) "" else "; thread dump is saved to '$file'")
          }
        }
      }
    }
  }

  override suspend fun processUnfinishedFreeze(consumer: suspend (Path, Int) -> Unit) {
    val files = try {
      withContext(Dispatchers.IO) {
        Files.newDirectoryStream(logDir) { it.fileName.toString().startsWith(THREAD_DUMPS_PREFIX) }.use { it.sorted() }
      }
    }
    catch (_: NoSuchFileException) {
      return
    }

    for (file in files) {
      val marker = file.resolve(DURATION_FILE_NAME)
      try {
        val duration = withContext(Dispatchers.IO) {
          if (Files.exists(marker)) {
            val duration = Files.readString(marker).toIntOrNull()
            Files.deleteIfExists(marker)
            duration
          }
          else {
            null
          }
        } ?: continue
        consumer(file, duration)
      }
      catch (_: Exception) { }
    }
  }

  @Suppress("SameParameterValue")
  private suspend fun samplePerformance(samplingIntervalMs: Long) {
    val current = System.nanoTime()
    var diffMs = TimeUnit.NANOSECONDS.toMillis(current - lastSampling) - samplingIntervalMs
    lastSampling = current

    // an unexpected delay of 3 seconds is considered as several delays: of 3, 2 and 1 seconds, because otherwise
    // this background thread would be sampled 3 times.
    while (diffMs >= 0) {
      generalApdex = generalApdex.withEvent(TOLERABLE_LATENCY, diffMs)
      diffMs -= samplingIntervalMs
    }
    jitWatcher.checkJitState()
    LOG.trace("Scheduling EDT sample")
    val latencyMs = withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      LOG.trace("Processing EDT sample")
      TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current)
    }
    swingApdex = swingApdex.withEvent(TOLERABLE_LATENCY, latencyMs)

    for (listener in EP_NAME.extensionList) {
      listener.uiResponded(latencyMs)
    }
  }

  /** for dump files on disk and in EA reports (ms)  */
  override val dumpInterval: Int
    get() = 5000.coerceIn(500, unresponsiveInterval)

  /** to limit the number of dumps and the size of performance snapshot  */
  override val maxDumpDuration: Int
    get() = (dumpInterval * 20).coerceIn(0, 40000) // 20 files max
  override val jitProblem: String?
    get() = jitWatcher.jitProblem

  /** defines the freeze (ms)  */
  override val unresponsiveInterval: Int
    get() {
      val value = unresponsiveIntervalLazy.asInteger()
      return if (value <= 0) 0 else value.coerceIn(500, 20000)
    }

  @ApiStatus.Internal
  override fun edtEventStarted() {
    if (!isActive) return
    stopCurrentTaskAndReEmit(FreezeCheckerTask(System.nanoTime()))
  }

  @ApiStatus.Internal
  override fun edtEventFinished() {
    if (!isActive) return
    stopCurrentTaskAndReEmit(null)
  }

  private fun stopCurrentTaskAndReEmit(task: FreezeCheckerTask?) {
    currentEdtEventChecker?.stop()
    currentEdtEventChecker = task
    check(taskFlow.tryEmit(task))
  }

  override fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, stripDump: Boolean): Path? =
    dumpThreads(pathPrefix, appendMillisecondsToFileName, contentsPrefix = "", stripDump)

  private fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, contentsPrefix: String, stripDump: Boolean): Path? {
    val rawDump = contentsPrefix + ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), stripDump).rawDump
    return dumpThreads(pathPrefix, appendMillisecondsToFileName, rawDump)
  }

  private fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, rawDump: String): Path? {
    var effectivePathPrefix = pathPrefix
    if (!effectivePathPrefix.contains('/')) {
      effectivePathPrefix = "$THREAD_DUMPS_PREFIX$effectivePathPrefix-${formatTime(ideStartTime)}-${buildName()}/"
    }
    else if (!effectivePathPrefix.startsWith(THREAD_DUMPS_PREFIX)) {
      effectivePathPrefix = THREAD_DUMPS_PREFIX + effectivePathPrefix
    }
    val now = ZonedDateTime.now()
    val suffix = if (appendMillisecondsToFileName) "-${now.toInstant().toEpochMilli()}" else ""
    val file = logDir.resolve("$effectivePathPrefix$DUMP_PREFIX${formatTime(now)}$suffix.txt")
    val dir = file.parent

    val memoryUsage = getMemoryUsage()
    if (!memoryUsage.isEmpty()) {
      LOG.info("$memoryUsage while dumping threads to $file")
    }

    try {
      Files.createDirectories(dir)
      Files.writeString(file, rawDump)
    }
    catch (e: IOException) {
      LOG.info("Failed to write the thread dump file", e)
    }
    return file
  }

  private fun getMemoryUsage(): String {
    val rt = Runtime.getRuntime()
    val maxMemory = rt.maxMemory()
    val usedMemory = rt.totalMemory() - rt.freeMemory()
    val freeMemory = maxMemory - usedMemory
    var diagnosticInfo = ""
    if (freeMemory < maxMemory / 5) {
      diagnosticInfo = "High memory usage (free ${freeMemory / 1024 / 1024} of ${maxMemory / 1024 / 1024} MB)"
    }
    val jitProblem = jitProblem
    if (jitProblem != null) {
      if (!diagnosticInfo.isEmpty()) {
        diagnosticInfo += ", "
      }
      diagnosticInfo += jitProblem
    }
    return diagnosticInfo
  }

  override fun clearFreezeStacktraces() {
    coroutineScope.launch {
      currentEdtEventChecker?.stopDumpingAsync()
    }
  }

  private inner class FreezeCheckerTask(private val taskStart: Long) {
    private val state = AtomicReference<CheckerState>(CheckerState.CHECKING)

    fun stop() {
      val oldState = state.getAndSet(CheckerState.FINISHED)
      if (oldState is CheckerState.FREEZE_LOGGING) {
        val task = oldState.dumpDask
        stopFreezeReporting(task)
      }
    }

    fun edtFrozen() {
      if (!state.compareAndSet(CheckerState.CHECKING, CheckerState.FREEZE_DETECTED)) {
        return
      }

      val dumpTask = startFreezeReporting()

      if (!state.compareAndSet(CheckerState.FREEZE_DETECTED, CheckerState.FREEZE_LOGGING(dumpTask))) {
        stopFreezeReporting(dumpTask)
      }
    }

    suspend fun stopDumpingAsync() {
      val oldState = state.getAndSet(CheckerState.FINISHED)
      if (oldState is CheckerState.FREEZE_LOGGING) {
        oldState.dumpDask.stopDumpingThreads()
      }
    }

    private fun startFreezeReporting(): MySamplingTask {
      val freezeFolder = "${THREAD_DUMPS_PREFIX}freeze-${formatTime(ZonedDateTime.now())}-${buildName()}"

      val reportDir = logDir.resolve(freezeFolder)
      Files.createDirectories(reportDir)

      for (listener in EP_NAME.extensionList) {
        listener.uiFreezeStarted(reportDir, coroutineScope)
      }
      val dumpTask = MySamplingTask(freezeFolder = freezeFolder, taskStart = taskStart)
      publisher?.uiFreezeStarted(reportDir)

      return dumpTask
    }

    private fun stopFreezeReporting(task: MySamplingTask) {
      val taskStop = System.nanoTime()
      coroutineScope.launch {
        task.stopDumpingThreads()

        val durationMs = TimeUnit.MILLISECONDS.convert(taskStop - taskStart, TimeUnit.NANOSECONDS)

        val freezeFolder = task.freezeFolder
        val freezeDir = logDir.resolve(freezeFolder)
        for (listener in EP_NAME.extensionList) {
          listener.uiFreezeFinished(durationMs, freezeDir)
        }
        publisher?.uiFreezeFinished(durationMs, freezeDir)

        val reportDir = postProcessReportFolder(durationMs = durationMs, task = task, dir = logDir.resolve(freezeFolder), logDir = logDir)

        for (listener in EP_NAME.extensionList) {
          listener.uiFreezeRecorded(durationMs, reportDir)
        }
      }
    }
  }

  inner class MySamplingTask(@JvmField val freezeFolder: String, private val taskStart: Long)
    : SamplingTask(dumpInterval = dumpInterval, maxDurationMs = maxDumpDuration, coroutineScope = coroutineScope) {
    override suspend fun dumpedThreads(threadDump: ThreadDump) {
      val file = dumpThreads(pathPrefix = "$freezeFolder/", appendMillisecondsToFileName = false, rawDump = threadDump.rawDump) ?: return
      try {
        val durationInSeconds = TimeUnit.SECONDS.convert(System.nanoTime() - taskStart, TimeUnit.NANOSECONDS)
        withContext(Dispatchers.IO) {
          val parent = file.parent
          Files.createDirectories(parent)
          Files.writeString(parent.resolve(DURATION_FILE_NAME), durationInSeconds.toString())
        }

        for (listener in EP_NAME.extensionList) {
          coroutineContext.ensureActive()
          listener.dumpedThreads(file, threadDump)
        }
        coroutineContext.ensureActive()
        publisher?.dumpedThreads(file, threadDump)
      }
      catch (e: IOException) {
        LOG.info("Failed to write the duration file", e)
      }
    }
  }

  override fun newSnapshot(): Snapshot = SnapshotImpl(this)

  private class SnapshotImpl(private val watcher: PerformanceWatcherImpl) : Snapshot {
    private val startGeneralSnapshot = watcher.generalApdex
    private val startSwingSnapshot = watcher.swingApdex
    private val startMillis = System.currentTimeMillis()

    override fun logResponsivenessSinceCreation(activityName: @NonNls String) {
      logResponsivenessSinceCreation(activityName, null)
    }

    override fun logResponsivenessSinceCreation(activityName: @NonNls String, spanName: String?) {
      LOG.info(getLogResponsivenessSinceCreationMessage(activityName, spanName))
    }

    override fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String): String =
      getLogResponsivenessSinceCreationMessage(activityName, null)

    override fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String, spanName: String?): String {
      val currentTime = System.currentTimeMillis()
      if (spanName != null) {
        TelemetryManager.getTracer(Scope("PerformanceWatcher"))
          .spanBuilder(spanName)
          .setStartTimestamp(startMillis, TimeUnit.MILLISECONDS)
          .startSpan()
          .end(currentTime, TimeUnit.MILLISECONDS)
      }
      return "$activityName took ${currentTime - startMillis}ms; general responsiveness: ${
        watcher.generalApdex.summarizePerformanceSince(startGeneralSnapshot)
      }; EDT responsiveness: ${watcher.swingApdex.summarizePerformanceSince(startSwingSnapshot)}"
    }
  }
}

private fun postProcessReportFolder(durationMs: Long, task: SamplingTask, dir: Path, logDir: Path): Path? {
  if (Files.notExists(dir)) {
    return null
  }

  cleanup(dir)
  var reportDir = logDir.resolve("${dir.name}${getFreezePlaceSuffix(task)}-${TimeUnit.MILLISECONDS.toSeconds(durationMs)}sec")
  try {
    Files.move(dir, reportDir)
  }
  catch (e: IOException) {
    LOG.warn("Unable to create freeze folder $reportDir", e)
    reportDir = dir
  }

  val message = "UI was frozen for ${durationMs}ms, details saved to $reportDir"

  if (DebugAttachDetector.isAttached()) {
    // so freezes produced by standing at breakpoint are not reported as exceptions
    LOG.info(message)
  }
  else if (ApplicationManagerEx.isInIntegrationTest()) {
    LOG.error(message)
  }
  else {
    LOG.warn(message)
  }

  return reportDir
}

private fun getFreezePlaceSuffix(task: SamplingTask): String {
  var stacktraceCommonPart: List<StackTraceElement>? = null
  for (info in task.threadInfos.asIterable()) {
    val edt = info.firstOrNull(ThreadDumper::isEDT) ?: continue
    val edtStack = edt.stackTrace ?: continue
    stacktraceCommonPart = if (stacktraceCommonPart == null) {
      edtStack.toList()
    }
    else {
      getStacktraceCommonPart(stacktraceCommonPart, edtStack)
    }
  }

  if (stacktraceCommonPart.isNullOrEmpty()) {
    return ""
  }

  val element = stacktraceCommonPart.first()
  return "-${sanitizeFileName(StringUtilRt.getShortName(element.className))}.${sanitizeFileName(element.methodName)}"
}

private suspend fun reportCrashesIfAny() {
  val systemDir = Path.of(PathManager.getSystemPath())
  val appInfoFile = systemDir.resolve(APP_INFO_FILE_NAME)
  val pidFile = systemDir.resolve(PID_FILE_NAME)
  // TODO: check jre in app info, not the current
  // Only report if on JetBrains jre
  if (SystemInfo.isJetBrainsJvm && Files.isRegularFile(appInfoFile) && Files.isRegularFile(pidFile)) {
    val crashInfo = withContext(Dispatchers.IO) {
      val pid = Files.readString(pidFile)
      val appInfoFileLastModified = Files.getLastModifiedTime(appInfoFile).toMillis()
      collectCrashInfo(pid, appInfoFileLastModified)
    }
    if (crashInfo != null) {
      val attachments = mutableListOf<Attachment>()

      if (crashInfo.jvmCrashContent != null) {
        IdeaFreezeReporter.checkProfilerCrash(crashInfo.jvmCrashContent)
        attachments += Attachment("crash.txt", crashInfo.jvmCrashContent).also { it.isIncluded = true }
      }

      // include plugins list
      attachments += Attachment(
        "plugins.txt",
        PluginManagerCore.loadedPlugins
          .asSequence()
          .filter { it.isEnabled && !it.isBundled }
          .map(::getPluginInfoByDescriptor)
          .filter(PluginInfo::isSafeToReport)
          .map { "${it.id} (${it.version})" }
          .joinToString(separator = "\n", "Extra plugins:\n")
      ).also { it.isIncluded = true }

      if (crashInfo.extraJvmLog != null) {
        // Detect crashes caused by OOME
        if (crashInfo.extraJvmLog.contains("java.lang.OutOfMemoryError: Java heap space")) {
          LowMemoryNotifier.showNotificationFromCrashAnalysis()
        }
        attachments += Attachment("jbr_err.txt", crashInfo.extraJvmLog).also { it.isIncluded = true }
      }

      if (crashInfo.osCrashContent != null) {
        attachments += Attachment("process_crash.txt", crashInfo.osCrashContent).also { it.isIncluded = true }
      }

      val message = crashInfo.jvmCrashContent?.substringBefore("---------------  P R O C E S S  ---------------")
                    ?: crashInfo.extraJvmLog
                    ?: crashInfo.osCrashContent
                    ?: "<no crash info retrieved>" // actually should never happen, but it's better than throwing, at least attachments are reported
      val event = LogMessage(JBRCrash(), message, attachments)
      event.appInfo = Files.readString(appInfoFile)
      IdeaFreezeReporter.report(event)
      LifecycleUsageTriggerCollector.onCrashDetected()
    }
  }

  IdeaFreezeReporter.saveAppInfo(appInfoFile, overwrite = true)
  withContext(Dispatchers.IO) {
    Files.createDirectories(pidFile.parent)
    Files.writeString(pidFile, ProcessHandle.current().pid().toString())
  }
}

internal val MacOSDiagnosticReportDirectories: List<String>
  get() = listOf(
    SystemProperties.getUserHome() + "/Library/Logs/DiagnosticReports",
    SystemProperties.getUserHome() + "/Library/Logs/DiagnosticReports/Retired",
  )

private const val CRASH_MAX_SIZE = 5 * FileUtilRt.MEGABYTE

private data class CrashInfo(val jvmCrashContent: String?, val extraJvmLog: String?, val osCrashContent: String?)

private fun collectCrashInfo(pid: String, lastModified: Long): CrashInfo? {
  val javaCrashContent = runCatching {
    val crashFiles = Path.of(SystemProperties.getUserHome()).useDirectoryEntries { entries -> entries
      .filter { it.name.startsWith("java_error_in") && it.name.endsWith("${pid}.log") && it.isRegularFile() && it.getLastModifiedTime().toMillis() > lastModified }
      .toList()
    }
    crashFiles.firstNotNullOfOrNull { file ->
      if (file.fileSize() > CRASH_MAX_SIZE) {
        LOG.info("Crash file $file is too big to report")
        return@firstNotNullOfOrNull null
      }
      return@firstNotNullOfOrNull Files.readString(file)
    }
  }.getOrLogException(LOG)

  // TODO: maybe we need to notify the user
  // see https://youtrack.jetbrains.com/issue/IDEA-258128
  if (javaCrashContent != null && javaCrashContent.contains("fuck_the_regulations")) {
    return null
  }

  val jbrErrContent = runCatching {
    findExtraLogFile(pid, lastModified)?.let { Files.readString(it) }
  }.getOrLogException(LOG)

  val osCrashContent = runCatching {
    if (!SystemInfoRt.isMac) return@runCatching null
    for (reportsDir in MacOSDiagnosticReportDirectories) {
      val reportFiles = Path.of(reportsDir).useDirectoryEntries { entries -> entries
        .filter { it.name.endsWith(".ips") && it.isRegularFile() && it.getLastModifiedTime().toMillis() > lastModified }
        .toList()
      }
      val osCrashContent = reportFiles.firstNotNullOfOrNull { file ->
        if (file.fileSize() > CRASH_MAX_SIZE) {
          LOG.info("OS crash file $file is too big to process or report")
          return@firstNotNullOfOrNull null
        }
        // https://developer.apple.com/documentation/xcode/interpreting-the-json-format-of-a-crash-report
        val content = Files.readString(file)
        if (!content.contains(pid)) return@firstNotNullOfOrNull null // certainly not our crash report
        try {
          val jsonObjects = content.splitToSequence("\r\n", "\n", "\r", limit = 2).toList()
          check(jsonObjects.size == 2) { content }
          val (metadata, report) = jsonObjects.map(Json::parseToJsonElement)
          metadata as JsonObject
          report as JsonObject
          if (metadata["bug_type"] == JsonPrimitive("309") && report["pid"] == JsonPrimitive(pid.toInt())) {
            return@firstNotNullOfOrNull content
          }
        }
        catch (e: Exception) {
          LOG.warn("failed to process MacOS diagnostic report $file", e)
        }
        null
      }
      if (osCrashContent != null) return@runCatching osCrashContent
    }
    return@runCatching null // not found
  }.getOrLogException(LOG)

  return if (javaCrashContent != null || jbrErrContent != null || osCrashContent != null) CrashInfo(javaCrashContent, jbrErrContent, osCrashContent) else null
}

private fun findExtraLogFile(pid: String, lastModified: Long): Path? {
  if (!SystemInfo.isMac) {
    return null
  }

  val logFileName = "jbr_err_pid$pid.log"
  return sequenceOf(Path.of(SystemProperties.getUserHome(), logFileName), Path.of(logFileName))
    .firstOrNull { file ->
      file.basicAttributesIfExists()?.let {
        it.isRegularFile && it.lastModifiedTime().toMillis() > lastModified
      } ?: false
    }
}

private val publisher: IdePerformanceListener?
  get() {
    val app = ApplicationManager.getApplication()
    return if (app == null || app.isDisposed) null else app.messageBus.syncPublisher(IdePerformanceListener.TOPIC)
  }

private fun cleanOldFiles(dir: Path, level: Int) {
  val children = try {
    Files.newDirectoryStream(dir) { level > 0 || it.fileName.toString().startsWith(THREAD_DUMPS_PREFIX) }.use { it.sorted() }
  }
  catch (_: NoSuchFileException) {
    return
  }

  for ((i, child) in children.withIndex()) {
    if (i < (children.size - 100) || ageInDays(child) > 10) {
      NioFiles.deleteRecursively(child)
    }
    else if (level < 3 && Files.isDirectory(child)) {
      cleanOldFiles(dir = child, level = level + 1)
    }
  }
}

private fun ageInDays(file: Path): Long =
  (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis()).toDuration(DurationUnit.MILLISECONDS).inWholeDays

/** for [PerformanceListener.uiResponded] events (ms)  */
private const val samplingInterval = 1000L

private fun buildName(): String = ApplicationInfo.getInstance().build.asString()

private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private fun formatTime(time: ZonedDateTime): String = dateFormat.format(time)

private fun cleanup(dir: Path) {
  Files.deleteIfExists(dir.resolve(DURATION_FILE_NAME))
}

internal fun getStacktraceCommonPart(commonPart: List<StackTraceElement>, stackTraceElements: Array<StackTraceElement>): List<StackTraceElement> {
  var i = 0
  while (i < commonPart.size && i < stackTraceElements.size) {
    val el1 = commonPart[commonPart.size - i - 1]
    val el2 = stackTraceElements[stackTraceElements.size - i - 1]
    if (!compareStackTraceElements(el1, el2)) {
      return commonPart.subList(commonPart.size - i, commonPart.size)
    }
    i++
  }
  return commonPart
}

// same as java.lang.StackTraceElement.equals, but do not care about the line number
internal fun compareStackTraceElements(el1: StackTraceElement, el2: StackTraceElement): Boolean =
  el1 === el2 || el1.className == el2.className && el1.methodName == el2.methodName && el1.fileName == el2.fileName

@Suppress("ClassName")
private sealed interface CheckerState {
  object CHECKING : CheckerState
  object FREEZE_DETECTED : CheckerState
  class FREEZE_LOGGING(val dumpDask: PerformanceWatcherImpl.MySamplingTask) : CheckerState
  object FINISHED : CheckerState
}
