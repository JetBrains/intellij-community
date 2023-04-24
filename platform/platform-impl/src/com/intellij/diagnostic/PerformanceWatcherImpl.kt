// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.execution.process.OSProcessUtil
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.MathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.AppScheduledExecutorService
import com.intellij.util.containers.ContainerUtil
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.io.File
import java.io.IOException
import java.lang.management.ThreadInfo
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import javax.swing.SwingUtilities

internal class PerformanceWatcherImpl private constructor() : PerformanceWatcher() {
  companion object {
    private val LOG = Logger.getInstance(PerformanceWatcherImpl::class.java)
    private const val TOLERABLE_LATENCY = 100
    private const val THREAD_DUMPS_PREFIX = "threadDumps-"
    private const val DURATION_FILE_NAME = ".duration"
    private const val PID_FILE_NAME = ".pid"
    private val ourIdeStart = System.currentTimeMillis()
    private fun reportCrashesIfAny() {
      val systemDir = Path.of(PathManager.getSystemPath())
      try {
        val appInfoFile = systemDir.resolve(IdeaFreezeReporter.APPINFO_FILE_NAME)
        val pidFile = systemDir.resolve(PID_FILE_NAME)
        // TODO: check jre in app info, not the current
        // Only report if on JetBrains jre
        if (SystemInfo.isJetBrainsJvm && Files.isRegularFile(appInfoFile) && Files.isRegularFile(pidFile)) {
          val pid = Files.readString(pidFile)
          val crashFiles = ((File(SystemProperties.getUserHome()).listFiles { file: File ->
            file.name.startsWith("java_error_in") && file.name.endsWith(
              "$pid.log") && file.isFile
          }) ?: arrayOfNulls(0))
          val appInfoFileLastModified = Files.getLastModifiedTime(appInfoFile).toMillis()
          for (file in crashFiles) {
            if (file!!.lastModified() > appInfoFileLastModified) {
              if (file.length() > 5 * FileUtilRt.MEGABYTE) {
                LOG.info("Crash file $file is too big to report")
                break
              }
              val content = FileUtil.loadFile(file)
              // TODO: maybe we need to notify the user
              if (content.contains("fuck_the_regulations")) {
                break
              }
              val attachment = Attachment("crash.txt", content)
              attachment.isIncluded = true

              // include plugins list
              val plugins = StreamEx.of(PluginManagerCore.getLoadedPlugins())
                .filter { d: IdeaPluginDescriptor? -> d!!.isEnabled && !d.isBundled }
                .map<PluginInfo> { plugin: PluginDescriptor -> getPluginInfoByDescriptor(plugin) }
                .filter { obj: PluginInfo -> obj.isSafeToReport() }
                .map<String> { (_, id, version): PluginInfo -> "$id ($version)" }
                .joining("\n", "Extra plugins:\n", "")
              val pluginsAttachment = Attachment("plugins.txt", plugins)
              attachment.isIncluded = true
              var attachments = arrayOf(attachment, pluginsAttachment)

              // look for extended crash logs
              val extraLog = findExtraLogFile(pid, appInfoFileLastModified)
              if (extraLog != null) {
                val jbrErrContent = FileUtil.loadFile(extraLog)
                // Detect crashes caused by OOME
                if (jbrErrContent.contains("java.lang.OutOfMemoryError: Java heap space")) {
                  LowMemoryNotifier.showNotification(VMOptions.MemoryKind.HEAP, true)
                }
                val extraAttachment = Attachment("jbr_err.txt", jbrErrContent)
                extraAttachment.isIncluded = true
                attachments = ArrayUtil.append(attachments, extraAttachment)
              }
              val message = StringUtil.substringBefore(content, "---------------  P R O C E S S  ---------------")
              val event = LogMessage.createEvent(JBRCrash(), message, *attachments)
              IdeaFreezeReporter.setAppInfo(event, Files.readString(appInfoFile))
              IdeaFreezeReporter.report(event)
              LifecycleUsageTriggerCollector.onCrashDetected()
              break
            }
          }
        }
        IdeaFreezeReporter.saveAppInfo(appInfoFile, true)
        Files.createDirectories(pidFile.parent)
        Files.writeString(pidFile, OSProcessUtil.getApplicationPid())
      }
      catch (e: IOException) {
        LOG.info(e)
      }
    }

    private fun findExtraLogFile(pid: String, lastModified: Long): File? {
      if (!SystemInfo.isMac) {
        return null
      }
      val logFileName = "jbr_err_pid$pid.log"
      val candidates = java.util.List.of(File(SystemProperties.getUserHome(), logFileName), File(logFileName))
      return ContainerUtil.find(candidates) { file: File -> file.isFile && file.lastModified() > lastModified }
    }

    private val publisher: IdePerformanceListener?
      private get() {
        val application = ApplicationManager.getApplication()
        return if (application != null && !application.isDisposed) application.messageBus.syncPublisher(IdePerformanceListener.TOPIC)
        else null
      }

    private fun cleanOldFiles(dir: File, level: Int) {
      val children = dir.listFiles { dir1: File?, name: String -> level > 0 || name.startsWith(THREAD_DUMPS_PREFIX) }
                     ?: return
      Arrays.sort(children)
      for (i in children.indices) {
        val child = children[i]
        if (i < children.size - 100 || ageInDays(child) > 10) {
          FileUtil.delete(child)
        }
        else if (level < 3) {
          cleanOldFiles(child, level + 1)
        }
      }
    }

    private fun ageInDays(file: File): Long {
      return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - file.lastModified())
    }

    private val samplingInterval: Int
      /** for [IdePerformanceListener.uiResponded] events (ms)  */
      private get() = 1000

    private fun buildName(): String {
      return ApplicationInfo.getInstance().build.asString()
    }

    private fun formatTime(timeMs: Long): String {
      return SimpleDateFormat("yyyyMMdd-HHmmss").format(Date(timeMs))
    }

    private fun cleanup(dir: File) {
      FileUtil.delete(File(dir, DURATION_FILE_NAME))
    }

    fun getStacktraceCommonPart(commonPart: List<StackTraceElement>,
                                stackTraceElements: Array<StackTraceElement>): List<StackTraceElement> {
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
    fun compareStackTraceElements(el1: StackTraceElement, el2: StackTraceElement): Boolean {
      return if (el1 === el2) {
        true
      }
      else el1.className == el2.className && el1.methodName == el2.methodName && el1.fileName == el2.fileName
    }
  }

  private val logDir = File(PathManager.getLogPath())

  @Volatile
  private var swingApdex = ApdexData.EMPTY

  @Volatile
  private var generalApdex = ApdexData.EMPTY

  @Volatile
  private var lastSampling = System.nanoTime()
  private var activeEvents = 0
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("EDT Performance Checker", 1)
  private var thread: ScheduledFuture<*>? = null
  private var currentEDTEventChecker: FreezeCheckerTask? = null
  private val jitWatcher = JitWatcher()
  private val unresponsiveInterval: RegistryValue

  init {
    val application = ApplicationManager.getApplication() ?: throw ExtensionNotApplicableException.create()
    val registryManager = application.getService(RegistryManager::class.java)
    unresponsiveInterval = registryManager["performance.watcher.unresponsive.interval.ms"]
    if (!application.isHeadlessEnvironment) {
      val cancelingListener: RegistryValueListener = object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          LOG.info("on UI freezes more than " + unresponsiveInterval + " ms will " +
                   "dump threads each " + dumpInterval + " ms for " + maxDumpDuration + " ms max")
          val samplingIntervalMs = samplingInterval
          cancelThread()
          thread = if (samplingIntervalMs <= 0) {
            null
          }
          else {
            executor.scheduleWithFixedDelay({ samplePerformance(samplingIntervalMs.toLong()) },
                                            samplingIntervalMs.toLong(),
                                            samplingIntervalMs.toLong(),
                                            TimeUnit.MILLISECONDS)
          }
        }
      }
      unresponsiveInterval.addListener(cancelingListener, this)
      if (ApplicationInfoImpl.getShadowInstance().isEAP) {
        val ourReasonableThreadPoolSize = registryManager["reasonable.application.thread.pool.size"]
        val service = AppExecutorUtil.getAppScheduledExecutorService() as AppScheduledExecutorService
        val AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors()
        service.setNewThreadListener { __: Thread?, ___: Runnable? ->
          val executorSize = service.backendPoolExecutorSize
          if (executorSize > ourReasonableThreadPoolSize.asInteger() + AVAILABLE_PROCESSORS) {
            val message = "Too many threads: $executorSize created in the global Application pool. ($ourReasonableThreadPoolSize, available processors: $AVAILABLE_PROCESSORS)"
            val file = doDumpThreads("newPooledThread/", true, message, true)
            LOG.info(message + if (file == null) "" else "; thread dump is saved to '" + file.path + "'")
          }
        }
      }
      reportCrashesIfAny()
      cleanOldFiles(logDir, 0)
      cancelingListener.afterValueChanged(unresponsiveInterval)
    }
  }

  override fun processUnfinishedFreeze(consumer: BiConsumer<in File, in Int?>) {
    val files = logDir.listFiles()
    if (files != null) {
      Arrays.stream(files)
        .filter { file: File -> file.name.startsWith(THREAD_DUMPS_PREFIX) }
        .filter { file: File -> Files.exists(file.toPath().resolve(DURATION_FILE_NAME)) }
        .findFirst().ifPresent { f: File ->
          val marker = File(f, DURATION_FILE_NAME)
          try {
            val s = FileUtil.loadFile(marker)
            cleanup(f)
            consumer.accept(f, s.toInt())
          }
          catch (ignored: Exception) {
          }
        }
    }
  }

  private fun cancelThread() {
    if (thread != null) {
      thread.cancel(true)
    }
  }

  override fun dispose() {
    cancelThread()
    executor.shutdownNow()
  }

  private fun samplePerformance(samplingIntervalMs: Long) {
    val current = System.nanoTime()
    var diffMs = TimeUnit.NANOSECONDS.toMillis(current - lastSampling) - samplingIntervalMs
    lastSampling = current

    // an unexpected delay of 3 seconds is considered as several delays: of 3, 2 and 1 seconds, because otherwise
    // this background thread would be sampled 3 times.
    while (diffMs >= 0) {
      generalApdex = generalApdex.withEvent(TOLERABLE_LATENCY.toLong(), diffMs)
      diffMs -= samplingIntervalMs
    }
    jitWatcher.checkJitState()
    SwingUtilities.invokeLater {
      val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - current)
      swingApdex = swingApdex.withEvent(TOLERABLE_LATENCY.toLong(), latencyMs)
      val publisher = publisher
      publisher?.uiResponded(latencyMs)
    }
  }

  /** for dump files on disk and in EA reports (ms)  */
  override fun getDumpInterval(): Int {
    return MathUtil.clamp(5000, 500, unresponsiveInterval)
  }

  /** defines the freeze (ms)  */
  override fun getUnresponsiveInterval(): Int {
    val value = unresponsiveInterval.asInteger()
    return if (value <= 0) 0 else MathUtil.clamp(value, 500, 20000)
  }

  /** to limit the number of dumps and the size of performance snapshot  */
  override fun getMaxDumpDuration(): Int {
    return MathUtil.clamp(dumpInterval * 20, 0, 40000) // 20 files max
  }

  @ApiStatus.Internal
  override fun edtEventStarted() {
    val start = System.nanoTime()
    activeEvents++
    if (thread != null) {
      if (currentEDTEventChecker != null) {
        currentEDTEventChecker!!.stop()
      }
      currentEDTEventChecker = FreezeCheckerTask(start)
    }
  }

  @ApiStatus.Internal
  override fun edtEventFinished() {
    activeEvents--
    if (thread != null) {
      Objects.requireNonNull(currentEDTEventChecker).stop()
      currentEDTEventChecker = if (activeEvents > 0) FreezeCheckerTask(System.nanoTime()) else null
    }
  }

  override fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, stripDump: Boolean): File? {
    return doDumpThreads(pathPrefix, appendMillisecondsToFileName, "", stripDump)
  }

  private fun doDumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, contentsPrefix: String, stripDump: Boolean): File? {
    return if (thread == null) null
    else dumpThreads(pathPrefix, appendMillisecondsToFileName,
                     contentsPrefix + ThreadDumper.getThreadDumpInfo(ThreadDumper.getThreadInfos(), stripDump).rawDump)
  }

  private fun dumpThreads(pathPrefix: String, appendMillisecondsToFileName: Boolean, rawDump: String): File? {
    var pathPrefix = pathPrefix
    if (!pathPrefix.contains("/")) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix + "-" + formatTime(ourIdeStart) + "-" + buildName() + "/"
    }
    else if (!pathPrefix.startsWith(THREAD_DUMPS_PREFIX)) {
      pathPrefix = THREAD_DUMPS_PREFIX + pathPrefix
    }
    val now = System.currentTimeMillis()
    val suffix = if (appendMillisecondsToFileName) "-$now" else ""
    val file = File(logDir, pathPrefix + DUMP_PREFIX + formatTime(now) + suffix + ".txt")
    val dir = file.parentFile
    if (!(dir.isDirectory || dir.mkdirs())) {
      return null
    }
    val memoryUsage = memoryUsage
    if (!memoryUsage.isEmpty()) {
      LOG.info("$memoryUsage while dumping threads to $file")
    }
    try {
      FileUtil.writeToFile(file, rawDump)
    }
    catch (e: IOException) {
      LOG.info("Failed to write the thread dump file: " + e.message)
    }
    return file
  }

  private val memoryUsage: String
    private get() {
      val rt = Runtime.getRuntime()
      val maxMemory = rt.maxMemory()
      val usedMemory = rt.totalMemory() - rt.freeMemory()
      val freeMemory = maxMemory - usedMemory
      var diagnosticInfo = ""
      if (freeMemory < maxMemory / 5) {
        diagnosticInfo = "High memory usage (free " + freeMemory / 1024 / 1024 + " of " + maxMemory / 1024 / 1024 + " MB)"
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

  override fun getJitProblem(): String? {
    return jitWatcher.jitProblem
  }

  override fun clearFreezeStacktraces() {
    if (currentEDTEventChecker != null) {
      currentEDTEventChecker!!.stopDumping()
    }
  }

  inner class SnapshotImpl private constructor() : Snapshot {
    private val myStartGeneralSnapshot = generalApdex
    private val myStartSwingSnapshot = swingApdex
    private val myStartMillis = System.currentTimeMillis()
    override fun logResponsivenessSinceCreation(activityName: @NonNls String) {
      LOG.info(getLogResponsivenessSinceCreationMessage(activityName))
    }

    override fun getLogResponsivenessSinceCreationMessage(activityName: @NonNls String): String {
      return activityName + " took " + (System.currentTimeMillis() - myStartMillis) + "ms" +
             "; general responsiveness: " + generalApdex.summarizePerformanceSince(myStartGeneralSnapshot) +
             "; EDT responsiveness: " + swingApdex.summarizePerformanceSince(myStartSwingSnapshot)
    }
  }

  override fun getExecutor(): ScheduledExecutorService {
    return executor
  }

  private enum class CheckerState {
    CHECKING,
    FREEZE,
    FINISHED
  }

  private inner class FreezeCheckerTask internal constructor(private val myTaskStart: Long) {
    private val myState = AtomicReference(CheckerState.CHECKING)
    private val myFuture: Future<*>
    private var myFreezeFolder: String? = null

    @Volatile
    private var myDumpTask: SamplingTask? = null

    init {
      myFuture = executor.schedule({ edtFrozen() },
                                   unresponsiveInterval.toLong(),
                                   TimeUnit.MILLISECONDS)
    }

    private fun getDuration(current: Long,
                            unit: TimeUnit): Long {
      return unit.convert(current - myTaskStart, TimeUnit.NANOSECONDS)
    }

    fun stop() {
      myFuture.cancel(false)
      if (myState.getAndSet(CheckerState.FINISHED) == CheckerState.FREEZE) {
        val taskStop = System.nanoTime()
        stopDumping() // stop sampling as early as possible
        try {
          executor.submit {
            stopDumping()
            val durationMs = getDuration(taskStop, TimeUnit.MILLISECONDS)
            val publisher = publisher
            publisher?.uiFreezeFinished(durationMs, File(logDir, myFreezeFolder))
            val reportDir = postProcessReportFolder(durationMs)
            publisher?.uiFreezeRecorded(durationMs, reportDir)
          }.get()
        }
        catch (e: Exception) {
          LOG.warn(e)
        }
      }
    }

    private fun edtFrozen() {
      myFreezeFolder = THREAD_DUMPS_PREFIX +
                       "freeze-" +
                       formatTime(System.currentTimeMillis()) + "-" + buildName()
      if (myState.compareAndSet(CheckerState.CHECKING, CheckerState.FREEZE)) {
        //TODO always true for some reason
        //myFreezeDuringStartup = !LoadingState.INDEXING_FINISHED.isOccurred();
        val reportDir = File(logDir, myFreezeFolder)
        reportDir.mkdirs()
        val publisher = publisher ?: return
        publisher.uiFreezeStarted(reportDir)
        myDumpTask = object : SamplingTask(dumpInterval, maxDumpDuration) {
          override fun dumpedThreads(threadDump: ThreadDump) {
            if (myState.get() == CheckerState.FINISHED) {
              stop()
            }
            else {
              val file = dumpThreads("$myFreezeFolder/",
                                     false,
                                     threadDump.rawDump)
              if (file != null) {
                try {
                  val duration = getDuration(System.nanoTime(), TimeUnit.SECONDS)
                  FileUtil.writeToFile(File(file.parentFile, DURATION_FILE_NAME),
                                       java.lang.Long.toString(duration))
                  publisher.dumpedThreads(file, threadDump)
                }
                catch (e: IOException) {
                  LOG.info("Failed to write the duration file: " + e.message)
                }
              }
            }
          }
        }
      }
    }

    private fun postProcessReportFolder(durationMs: Long): File? {
      val dir = File(logDir, myFreezeFolder)
      var reportDir: File? = null
      if (dir.exists()) {
        cleanup(dir)
        reportDir = File(logDir, dir.name + freezePlaceSuffix + "-" + TimeUnit.MILLISECONDS.toSeconds(durationMs) + "sec")
        if (!dir.renameTo(reportDir)) {
          LOG.warn("Unable to create freeze folder $reportDir")
          reportDir = dir
        }
        val message = "UI was frozen for " + durationMs + "ms, details saved to " + reportDir
        if (PluginManagerCore.isRunningFromSources()) {
          LOG.info(message)
        }
        else {
          LOG.warn(message)
        }
      }
      return reportDir
    }

    fun stopDumping() {
      val task = myDumpTask
      if (task != null) {
        task.stop()
        myDumpTask = null
      }
    }

    private val freezePlaceSuffix: String
      private get() {
        var stacktraceCommonPart: List<StackTraceElement>? = null
        val task = myDumpTask ?: return ""
        for (info in task.threadInfos) {
          val edt = ContainerUtil.find(info) { info: ThreadInfo? ->
            ThreadDumper.isEDT(
              info!!)
          }
          if (edt != null) {
            val edtStack = edt.stackTrace
            if (edtStack != null) {
              stacktraceCommonPart = if (stacktraceCommonPart == null) {
                Arrays.asList(*edtStack)
              }
              else {
                getStacktraceCommonPart(stacktraceCommonPart, edtStack)
              }
            }
          }
        }
        if (!ContainerUtil.isEmpty(stacktraceCommonPart)) {
          val element = stacktraceCommonPart!![0]
          return "-" +
                 FileUtil.sanitizeFileName(StringUtil.getShortName(element.className)) +
                 "." +
                 FileUtil.sanitizeFileName(element.methodName)
        }
        return ""
      }
  }

  override fun newSnapshot(): Snapshot {
    return SnapshotImpl()
  }
}
