// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.diagnostic

import com.intellij.execution.process.OSProcessUtil
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.registry.useRegistryManagerWhenReady
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.io.basicAttributesIfExists
import com.intellij.util.io.sanitizeFileName
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.io.path.name
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

@OptIn(ExperimentalCoroutinesApi::class)
internal class PerformanceWatcherImpl(private val coroutineScope: CoroutineScope) : PerformanceWatcher() {
  private val logDir = PathManager.getLogDir()

  @Volatile
  private var swingApdex = ApdexData.EMPTY

  @Volatile
  private var generalApdex = ApdexData.EMPTY

  @Volatile
  private var lastSampling = System.nanoTime()
  private var activeEvents = 0
  private val limitedDispatcher = Dispatchers.Default.limitedParallelism(1)
  private var sampleJob: Job? = null
  private var currentEdtEventChecker: FreezeCheckerTask? = null
  private val jitWatcher = JitWatcher()
  private val unresponsiveIntervalLazy by lazy {
    RegistryManager.getInstance().get("performance.watcher.unresponsive.interval.ms")
  }

  init {
    if (!ApplicationManager.getApplication().isHeadlessEnvironment) {
      coroutineScope.launch {
        useRegistryManagerWhenReady { registryManager ->
          val unresponsiveInterval = unresponsiveIntervalLazy
          init(unresponsiveInterval, registryManager)
        }
      }
    }
  }

  private suspend fun init(unresponsiveInterval: RegistryValue, registryManager: RegistryManager) {
    val cancelingListener = object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        LOG.info("on UI freezes more than ${unresponsiveInterval} ms will dump threads each $dumpInterval ms for $maxDumpDuration ms max")
        val samplingIntervalMs = samplingInterval
        sampleJob?.cancel()
        @Suppress("KotlinConstantConditions")
        sampleJob = if (samplingIntervalMs <= 0) {
          null
        }
        else {
          coroutineScope.launch(limitedDispatcher) {
            while (true) {
              delay(samplingIntervalMs)
              samplePerformance(samplingIntervalMs)
            }
          }
        }
      }
    }
    unresponsiveInterval.addListener(cancelingListener, this)
    if (ApplicationInfoImpl.getShadowInstance().isEAP) {
      coroutineScope.launch {
        val reasonableThreadPoolSize = registryManager.get("reasonable.application.thread.pool.size")
        val service = AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService
        val allAvailableProcessors = Runtime.getRuntime().availableProcessors()
        service.setNewThreadListener { _, _ ->
          val executorSize = service.backendPoolExecutorSize
          if (executorSize > reasonableThreadPoolSize.asInteger() + allAvailableProcessors) {
            val message = "Too many threads: $executorSize created in the global Application pool. " +
                          "($reasonableThreadPoolSize, available processors: $allAvailableProcessors)"
            val file = doDumpThreads("newPooledThread/", true, message, true)
            LOG.info(message + if (file == null) "" else "; thread dump is saved to '$file'")
          }
        }
      }
    }

    runCatching {
      reportCrashesIfAny()
    }.getOrLogException(LOG)

    withContext(Dispatchers.IO) {
      cleanOldFiles(logDir, 0)
    }
    cancelingListener.afterValueChanged(unresponsiveInterval)
  }

  override suspend fun processUnfinishedFreeze(consumer: suspend (Path, Int) -> Unit) {
    val files = try {
      withContext(Dispatchers.IO) {
        Files.newDirectoryStream(logDir) { it.fileName.toString().startsWith(THREAD_DUMPS_PREFIX) }.use { it.sorted() }
      }
    }
    catch (ignore: NoSuchFileException) {
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
      catch (ignored: Exception) {
      }
    }
  }

  override fun dispose() {
    sampleJob?.cancel()
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
    val latencyMs = withContext(Dispatchers.EDT) {
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
    val start = System.nanoTime()
    activeEvents++
    if (sampleJob != null) {
      currentEdtEventChecker?.stop()
      currentEdtEventChecker = FreezeCheckerTask(start)
    }
  }

  @ApiStatus.Internal
  override fun edtEventFinished() {
    activeEvents--
    if (sampleJob != null) {
      currentEdtEventChecker?.stop()
      currentEdtEventChecker = if (activeEvents > 0) FreezeCheckerTask(System.nanoTime()) else null
    }
  }

  override fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, stripDump: Boolean): Path? {
    return doDumpThreads(pathPrefix = pathPrefix,
                         appendMillisecondsToFileName = appendMillisecondsToFileName,
                         contentsPrefix = "",
                         stripDump = stripDump)
  }

  private fun doDumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, contentsPrefix: String, stripDump: Boolean): Path? {
    if (sampleJob == null) {
      return null
    }
    return dumpThreads(pathPrefix = pathPrefix,
                       appendMillisecondsToFileName = appendMillisecondsToFileName,
                       rawDump = contentsPrefix + ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), stripDump).rawDump)
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
      currentEdtEventChecker?.stopDumping()
    }
  }

  private inner class FreezeCheckerTask(private val taskStart: Long) {
    private val state = AtomicReference(CheckerState.CHECKING)
    private val job: Job
    private var freezeFolder: String? = null

    @Volatile
    private var dumpTask: SamplingTask? = null

    init {
      job = coroutineScope.launch(limitedDispatcher) {
        delay(unresponsiveInterval.toLong())
        edtFrozen()
      }
    }

    private fun getDuration(current: Long, unit: TimeUnit): Long {
      return unit.convert(current - taskStart, TimeUnit.NANOSECONDS)
    }

    fun stop() {
      job.cancel()
      if (state.getAndSet(CheckerState.FINISHED) == CheckerState.FREEZE) {
        val taskStop = System.nanoTime()
        // stop sampling as early as possible
        stopDumping()
        try {
          coroutineScope.launch(limitedDispatcher) {
            stopDumping()
            val durationMs = getDuration(taskStop, TimeUnit.MILLISECONDS)

            val freezeDir = logDir.resolve(freezeFolder!!)
            for (listener in EP_NAME.extensionList) {
              listener.uiFreezeFinished(durationMs, freezeDir)
            }
            publisher?.uiFreezeFinished(durationMs, freezeDir)

            val reportDir = postProcessReportFolder(durationMs)

            for (listener in EP_NAME.extensionList) {
              listener.uiFreezeRecorded(durationMs, reportDir)
            }
          }.asCompletableFuture().join()
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
      }
    }

    private suspend fun edtFrozen() {
      freezeFolder = "${THREAD_DUMPS_PREFIX}freeze-${formatTime(ZonedDateTime.now())}-${buildName()}"
      if (!state.compareAndSet(CheckerState.CHECKING, CheckerState.FREEZE)) {
        return
      }

      //TODO always true for some reason
      //myFreezeDuringStartup = !LoadingState.INDEXING_FINISHED.isOccurred();
      val reportDir = logDir.resolve(freezeFolder!!)
      Files.createDirectories(reportDir)

      for (listener in EP_NAME.extensionList) {
        listener.uiFreezeStarted(reportDir, coroutineScope)
      }
      publisher?.uiFreezeStarted(reportDir)

      dumpTask = object : SamplingTask(dumpInterval = dumpInterval, maxDurationMs = maxDumpDuration, coroutineScope = coroutineScope) {
        override suspend fun dumpedThreads(threadDump: ThreadDump) {
          if (state.get() == CheckerState.FINISHED) {
            stop()
            return
          }

          val file = dumpThreads(pathPrefix = "$freezeFolder/", appendMillisecondsToFileName = false, rawDump = threadDump.rawDump)
                     ?: return
          try {
            val duration = getDuration(System.nanoTime(), TimeUnit.SECONDS)
            withContext(Dispatchers.IO) {
              Files.createDirectories(file.parent)
              Files.writeString(file.parent.resolve(DURATION_FILE_NAME), duration.toString())
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
    }

    private fun postProcessReportFolder(durationMs: Long): Path? {
      val dir = logDir.resolve(freezeFolder!!)
      if (!Files.exists(dir)) {
        return null
      }

      cleanup(dir)
      var reportDir = logDir.resolve("${dir.name}${getFreezePlaceSuffix()}-${TimeUnit.MILLISECONDS.toSeconds(durationMs)}sec")
      try {
        Files.move(dir, reportDir)
      }
      catch (e: IOException) {
        LOG.warn("Unable to create freeze folder $reportDir", e)
        reportDir = dir
      }
      val message = "UI was frozen for ${durationMs}ms, details saved to $reportDir"
      if (PluginManagerCore.isRunningFromSources()) {
        LOG.info(message)
      }
      else {
        LOG.warn(message)
      }
      return reportDir
    }

    fun stopDumping() {
      val task = dumpTask ?: return
      task.stop()
      dumpTask = null
    }

    private fun getFreezePlaceSuffix(): String {
      val task = dumpTask ?: return ""
      var stacktraceCommonPart: List<StackTraceElement>? = null
      for (info in task.threadInfos) {
        val edt = info.firstOrNull(ThreadDumper::isEDT) ?: continue
        val edtStack = edt.stackTrace ?: continue
        stacktraceCommonPart = if (stacktraceCommonPart == null) {
          edtStack.toList()
        }
        else {
          getStacktraceCommonPart(stacktraceCommonPart, edtStack)
        }
      }

      if (!stacktraceCommonPart.isNullOrEmpty()) {
        val element = stacktraceCommonPart[0]
        return "-${sanitizeFileName(StringUtilRt.getShortName(element.className))}.${sanitizeFileName(element.methodName)}"
      }
      return ""
    }
  }

  override fun newSnapshot(): Snapshot = SnapshotImpl(this)

  private class SnapshotImpl(private val watcher: PerformanceWatcherImpl) : Snapshot {
    private val myStartGeneralSnapshot = watcher.generalApdex
    private val myStartSwingSnapshot = watcher.swingApdex
    private val myStartMillis = System.currentTimeMillis()

    override fun logResponsivenessSinceCreation(activityName: @NonNls String) {
      LOG.info(getLogResponsivenessSinceCreationMessage(activityName))
    }

    override fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String): String {
      return activityName + " took " + (System.currentTimeMillis() - myStartMillis) + "ms" +
             "; general responsiveness: " + watcher.generalApdex.summarizePerformanceSince(myStartGeneralSnapshot) +
             "; EDT responsiveness: " + watcher.swingApdex.summarizePerformanceSince(myStartSwingSnapshot)
    }
  }
}

private suspend fun reportCrashesIfAny() {
  val systemDir = Path.of(PathManager.getSystemPath())
  val appInfoFile = systemDir.resolve(APP_INFO_FILE_NAME)
  val pidFile = systemDir.resolve(PID_FILE_NAME)
  // TODO: check jre in app info, not the current
  // Only report if on JetBrains jre
  if (SystemInfo.isJetBrainsJvm && Files.isRegularFile(appInfoFile) && Files.isRegularFile(pidFile)) {
    val pid = Files.readString(pidFile)
    val crashFiles = ((File(SystemProperties.getUserHome()).listFiles { file ->
      file.name.startsWith("java_error_in") && file.name.endsWith("$pid.log") && file.isFile
    }) ?: arrayOfNulls(0))
    val appInfoFileLastModified = Files.getLastModifiedTime(appInfoFile).toMillis()
    for (file in crashFiles) {
      if (file!!.lastModified() > appInfoFileLastModified) {
        if (file.length() > 5 * FileUtilRt.MEGABYTE) {
          LOG.info("Crash file $file is too big to report")
          break
        }

        val content = Files.readString(file.toPath())
        // TODO: maybe we need to notify the user
        if (content.contains("fuck_the_regulations")) {
          break
        }

        val attachment = Attachment("crash.txt", content)
        attachment.isIncluded = true

        // include plugins list
        val plugins = PluginManagerCore.getLoadedPlugins()
          .asSequence()
          .filter { it.isEnabled && !it.isBundled }
          .map(::getPluginInfoByDescriptor)
          .filter(PluginInfo::isSafeToReport)
          .map { "${it.id} (${it.version})" }
          .joinToString(separator = "\n", "Extra plugins:\n")
        val pluginAttachment = Attachment("plugins.txt", plugins)
        attachment.isIncluded = true
        val attachments = mutableListOf(attachment, pluginAttachment)

        // look for extended crash logs
        val extraLog = findExtraLogFile(pid, appInfoFileLastModified)
        if (extraLog != null) {
          val jbrErrContent = Files.readString(extraLog)
          // Detect crashes caused by OOME
          if (jbrErrContent.contains("java.lang.OutOfMemoryError: Java heap space")) {
            LowMemoryNotifier.showNotification(VMOptions.MemoryKind.HEAP, true)
          }
          val extraAttachment = Attachment("jbr_err.txt", jbrErrContent)
          extraAttachment.isIncluded = true
          attachments.add(extraAttachment)
        }
        val message = content.substringBefore("---------------  P R O C E S S  ---------------")
        val event = LogMessage.eventOf(JBRCrash(), message, attachments)
        IdeaFreezeReporter.setAppInfo(event, Files.readString(appInfoFile))
        IdeaFreezeReporter.report(event)
        LifecycleUsageTriggerCollector.onCrashDetected()
        break
      }
    }
  }
  IdeaFreezeReporter.saveAppInfo(appInfoFile, true)
  withContext(Dispatchers.IO) {
    Files.createDirectories(pidFile.parent)
    Files.writeString(pidFile, OSProcessUtil.getApplicationPid())
  }
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
  catch (ignore: NoSuchFileException) {
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

private fun ageInDays(file: Path): Long {
  return (System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis()).toDuration(DurationUnit.MILLISECONDS).inWholeDays
}

/** for [PerformanceListener.uiResponded] events (ms)  */
private const val samplingInterval = 1000L

private fun buildName(): String = ApplicationInfo.getInstance().build.asString()

@Suppress("SpellCheckingInspection")
private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

private fun formatTime(time: ZonedDateTime): String = dateFormat.format(time)

private fun cleanup(dir: Path) {
  Files.deleteIfExists(dir.resolve(DURATION_FILE_NAME))
}

internal fun getStacktraceCommonPart(commonPart: List<StackTraceElement>,
                                     stackTraceElements: Array<StackTraceElement>): List<StackTraceElement> {
  var i = 0
  while (i < commonPart.size && i < stackTraceElements.size) {
    val el1 = commonPart.get(commonPart.size - i - 1)
    val el2 = stackTraceElements[stackTraceElements.size - i - 1]
    if (!compareStackTraceElements(el1, el2)) {
      return commonPart.subList(commonPart.size - i, commonPart.size)
    }
    i++
  }
  return commonPart
}

// same as java.lang.StackTraceElement.equals, but do not care about the line number
internal fun compareStackTraceElements(el1: StackTraceElement, el2: StackTraceElement): Boolean {
  if (el1 === el2) {
    return true
  }
  else {
    return el1.className == el2.className && el1.methodName == el2.methodName && el1.fileName == el2.fileName
  }
}

private enum class CheckerState {
  CHECKING,
  FREEZE,
  FINISHED
}