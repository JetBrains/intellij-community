// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "CompanionObjectInExtension")

package com.intellij.diagnostic

import com.intellij.diagnostic.ITNProxy.appInfoString
import com.intellij.diagnostic.IdeErrorsDialog.Companion.getSubmitter
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.idea.AppMode
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.util.SmartList
import kotlinx.coroutines.*
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.management.ThreadInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

internal class IdeaFreezeReporter : PerformanceListener {
  private var dumpTask: SamplingTask? = null
  private val currentDumps = ArrayList<ThreadDump>()
  private var stacktraceCommonPart: List<StackTraceElement>? = null

  @Volatile
  private var appClosing = false

  init {
    val app = ApplicationManager.getApplication()
    if (app.isUnitTestMode || app.isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }

    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      app.messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appWillBeClosed(isRestart: Boolean) {
          appClosing = true
        }
      })

      if (DEBUG || (!PluginManagerCore.isRunningFromSources() && !AppMode.isDevServer())) {
        reportUnfinishedFreezes()
      }
    }

    if (!DEBUG && (PluginManagerCore.isRunningFromSources() || AppMode.isDevServer()) || !isEnabled(app)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  companion object {
    internal fun setAppInfo(event: IdeaLoggingEvent, appInfo: String?) {
      val data = event.data
      if (data is AbstractMessage) {
        data.appInfo = appInfo
      }
    }

    internal fun saveAppInfo(appInfoFile: Path, overwrite: Boolean) {
      if (overwrite || !Files.exists(appInfoFile)) {
        Files.createDirectories(appInfoFile.parent)
        Files.writeString(appInfoFile, appInfoString)
      }
    }

    internal fun report(event: IdeaLoggingEvent?) {
      if (event != null) {
        val t = event.throwable
        // only report to JB
        if (getSubmitter(t, PluginUtil.getInstance().findPluginId(t)) is ITNReporter) {
          MessagePool.getInstance().addIdeFatalMessage(event)
        }
      }
    }

    internal fun checkProfilerCrash(crashContent: String) {
      EP_NAME.forEachExtensionSafe { it.checkCrash(crashContent) }
    }
  }

  override fun uiFreezeStarted(reportDir: Path, coroutineScope: CoroutineScope) {
    if (DEBUG || !DebugAttachDetector.isAttached()) {
      dumpTask?.stop()

      reset()
      val watcher = PerformanceWatcher.getInstance()
      val maxDumpDuration = watcher.maxDumpDuration
      if (maxDumpDuration == 0) {
        return
      }

      dumpTask = object : SamplingTask(100, maxDumpDuration, coroutineScope) {
        override fun stop() {
          super.stop()
          EP_NAME.forEachExtensionSafe(FreezeProfiler::stop)
        }

        override suspend fun stopDumpingThreads() {
          super.stopDumpingThreads()
          EP_NAME.forEachExtensionSafe(FreezeProfiler::stop)
        }
      }
      EP_NAME.forEachExtensionSafe { it.start(reportDir) }
    }
  }

  override fun dumpedThreads(toFile: Path, dump: ThreadDump) {
    val dumpTask = dumpTask ?: return

    currentDumps.add(dump)
    val edtStack = dump.edtStackTrace
    if (edtStack != null) {
      stacktraceCommonPart = if (stacktraceCommonPart == null) {
        @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
        java.util.List.of(*edtStack)
      }
      else {
        getStacktraceCommonPart(stacktraceCommonPart!!, edtStack)
      }
    }
    val dir = toFile.parent
    val performanceWatcher = PerformanceWatcher.getInstance()
    val event = createEvent(dumpTask = dumpTask,
                            duration = dumpTask.totalTime + performanceWatcher.unresponsiveInterval,
                            attachments = emptyList(),
                            reportDir = dir,
                            performanceWatcher = performanceWatcher,
                            finished = false) ?: return
    try {
      Files.createDirectories(dir)
      Files.writeString(dir.resolve(MESSAGE_FILE_NAME), event.message)
      ObjectOutputStream(Files.newOutputStream(dir.resolve(THROWABLE_FILE_NAME))).use { it.writeObject(event.throwable) }
      saveAppInfo(dir.resolve(APP_INFO_FILE_NAME), false)
    }
    catch (ignored: IOException) {
    }
  }

  override fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {
    (dumpTask ?: return).stop()
    reportDir?.let { cleanup(it) }
  }

  override fun uiFreezeRecorded(durationMs: Long, reportDir: Path?) {
    val dumpTask = dumpTask
    if (dumpTask == null) {
      return
    }

    if (Registry.`is`("freeze.reporter.enabled", false)) {
      val performanceWatcher = PerformanceWatcher.getInstance()
      // check that we have at least half of the dumps required
      if ((durationMs / 1000).toInt() > FREEZE_THRESHOLD && !stacktraceCommonPart.isNullOrEmpty()) {
        val dumpingDurationMs = durationMs - performanceWatcher.unresponsiveInterval
        val dumpsCount = min(performanceWatcher.maxDumpDuration.toLong(), dumpingDurationMs / 2) / performanceWatcher.dumpInterval
        if (dumpTask.isValid(dumpingDurationMs) || currentDumps.size >= max(3, dumpsCount)) {
          val attachments = ArrayList<Attachment>()
          addDumpsAttachments(from = currentDumps, textMapper = { it.rawDump }, container = attachments)
          if (reportDir != null) {
            EP_NAME.forEachExtensionSafe { attachments.addAll(it.getAttachments(reportDir)) }
          }
          report(createEvent(dumpTask = dumpTask,
                             duration = durationMs,
                             attachments = attachments,
                             reportDir = reportDir,
                             performanceWatcher = performanceWatcher,
                             finished = true))
        }
      }
    }

    this.dumpTask = null
    reset()
  }

  private fun reset() {
    currentDumps.clear()
    stacktraceCommonPart = null
  }

  private fun createEvent(dumpTask: SamplingTask,
                          duration: Long,
                          attachments: List<Attachment>,
                          reportDir: Path?,
                          performanceWatcher: PerformanceWatcher,
                          finished: Boolean): IdeaLoggingEvent? {
    var infos: List<Array<ThreadInfo>> = dumpTask.threadInfos
    val dumpInterval = (if (infos.isEmpty()) performanceWatcher.dumpInterval else dumpTask.dumpInterval).toLong()
    if (infos.isEmpty()) {
      infos = currentDumps.map { it.threadInfos }
    }

    return createEvent(dumpTask = dumpTask,
                       duration = duration,
                       dumpInterval = dumpInterval,
                       sampledCount = infos.size,
                       causeThreads = infos.mapNotNull { getCauseThread(it) },
                       attachments = attachments,
                       reportDir = reportDir,
                       jitProblem = performanceWatcher.jitProblem,
                       finished = finished)
  }

  private fun createEvent(dumpTask: SamplingTask,
                          duration: Long,
                          dumpInterval: Long,
                          sampledCount: Int,
                          causeThreads: List<ThreadInfo>,
                          attachments: List<Attachment>,
                          reportDir: Path?,
                          jitProblem: String?,
                          finished: Boolean): IdeaLoggingEvent? {
    val allInEdt = causeThreads.all { ThreadDumper.isEDT(it) }
    val root = buildTree(threadInfos = causeThreads, time = dumpInterval)
    val classLoadingRatio = countClassLoading(causeThreads) * 100 / causeThreads.size
    val commonStackNode = root.findDominantCommonStack((causeThreads.size * dumpInterval * COMMON_SUB_STACK_WEIGHT).toLong())
    var commonStack = commonStackNode?.getStack()
    var nonEdtCause = false

    // fallback to simple EDT common
    if (commonStack.isNullOrEmpty()) {
      commonStack = stacktraceCommonPart
    }
    else {
      nonEdtCause = !ThreadDumper.isEDT(commonStackNode!!.threadInfo!!)
    }

    val reportText = root.dump()
    try {
      if (reportDir != null) {
        Files.createDirectories(reportDir)
        Files.writeString(reportDir.resolve("$REPORT_PREFIX.txt"), reportText)
      }
    }
    catch (ignored: IOException) {
    }

    if (commonStack.isNullOrEmpty() || commonStack.any { skippedFrame(it) }) {
      return null
    }

    val durationInSeconds = duration / 1000
    val edtNote = if (allInEdt) "in EDT " else ""
    var message = """Freeze ${edtNote}for $durationInSeconds seconds
${if (finished) "" else if (appClosing) "IDE is closing. " else "IDE KILLED! "}Sampled time: ${sampledCount * dumpInterval}ms, sampling rate: ${dumpInterval}ms"""
    if (jitProblem != null) {
      message += ", $jitProblem"
    }
    val total = dumpTask.totalTime
    val gcTime = dumpTask.gcTime
    if (total > 0) {
      message += ", GC time: ${gcTime}ms (${gcTime * 100 / total}%), Class loading: $classLoadingRatio%"
    }
    if (DebugAttachDetector.isDebugEnabled()) {
      message += ", debug agent: on"
    }
    val processCpuLoad = dumpTask.processCpuLoad
    if (processCpuLoad > 0) {
      message += ", cpu load: ${(processCpuLoad * 100).toInt()}%"
    }
    if (nonEdtCause) {
      message += "\n\nThe stack is from the thread that was blocking EDT"
    }
    val report = createReportAttachment(durationInSeconds, reportText)
    return LogMessage.eventOf(Freeze(commonStack), message, attachments + report)
  }
}

private class CallTreeNode(private val stackTraceElement: StackTraceElement?,
                           private val parent: CallTreeNode?,
                           @JvmField var time: Long,
                           @JvmField val threadInfo: ThreadInfo?) {
  private val children = SmartList<CallTreeNode>()
  private val depth: Int = if (parent == null) 0 else parent.depth + 1

  fun addCallee(e: StackTraceElement?, time: Long, threadInfo: ThreadInfo?): CallTreeNode {
    for (child in children) {
      if (compareStackTraceElements(child.stackTraceElement!!, e!!)) {
        child.time += time
        return child
      }
    }
    val child = CallTreeNode(stackTraceElement = e, parent = this, time = time, threadInfo = threadInfo)
    children.add(child)
    return child
  }

  fun getMostHitChild(): CallTreeNode? {
    var currentMax: CallTreeNode? = null
    for (child in children) {
      if (currentMax == null || child.time > currentMax.time) {
        currentMax = child
      }
    }
    return currentMax
  }

  override fun toString(): String = "$time $stackTraceElement"

  fun appendIndentedString(builder: StringBuilder) {
    repeat(depth) { builder.append(' ') }
    builder.append(stackTraceElement!!.className).append(".").append(stackTraceElement.methodName).append(" ").append(time).append(
      "ms").append('\n')
  }

  fun dump(): String {
    val stringBuilder = StringBuilder()
    val nodes = LinkedList(children)
    while (!nodes.isEmpty()) {
      val node = nodes.removeFirst()
      node.appendIndentedString(stringBuilder)
      nodes.addAll(0, node.children.sortedWith(TIME_COMPARATOR))
    }
    return stringBuilder.toString()
  }

  fun getStack(): List<StackTraceElement> {
    val result = ArrayList<StackTraceElement>()
    var node: CallTreeNode? = this
    while (true) {
      result.add((node?.stackTraceElement ?: break))
      node = node.parent
    }
    return result
  }

  fun findDominantCommonStack(threshold: Long): CallTreeNode? { // find dominant
    var node: CallTreeNode? = getMostHitChild() ?: return null
    while (!node?.children.isNullOrEmpty()) {
      val mostHitChild = node!!.getMostHitChild()
      if (mostHitChild != null && mostHitChild.time > threshold) {
        node = mostHitChild
      }
      else {
        break
      }
    }
    return node
  }
}

private val TIME_COMPARATOR: Comparator<CallTreeNode> = Comparator.comparingLong<CallTreeNode> { it.time }.reversed()

private fun buildTree(threadInfos: List<ThreadInfo>, time: Long): CallTreeNode {
  val root = CallTreeNode(null, null, 0, null)
  for (thread in threadInfos) {
    var node = root
    val stack = thread.stackTrace
    for (i in stack.indices.reversed()) {
      node = node.addCallee(stack[i], time, thread)
    }
  }
  return root
}

private val EP_NAME = ExtensionPointName<FreezeProfiler>("com.intellij.diagnostic.freezeProfiler")

// intentionally hardcoded and not implemented via a registry key or system property
// to be updated when we ready to collect freezes from the specified duration and up
private const val FREEZE_THRESHOLD = 10
private const val REPORT_PREFIX = "report"
private const val DUMP_PREFIX = "dump"
private const val MESSAGE_FILE_NAME = ".message"
private const val THROWABLE_FILE_NAME = ".throwable"

@Suppress("SpellCheckingInspection")
internal const val APP_INFO_FILE_NAME: String = ".appinfo"

// common sub-stack contains more than the specified % samples
private const val COMMON_SUB_STACK_WEIGHT = 0.25

/**
 * Set DEBUG = true to enable freeze-detection regardless of other settings.
 *
 * By default, freeze detection is off for IDE running from sources -- to filter out freezes during development and especially
 * during debugging.
 * Freeze detection could also be disabled with sys('idea.force.freeze.reports') variable (see [.isEnabled] for details).
 * DEBUG = true overrides all this, and enables freeze detection anyway
 * -- useful, e.g., while developing/debugging freeze detection code itself.
 */
private const val DEBUG = false

private suspend fun reportUnfinishedFreezes() {
  ApplicationManager.getApplication().serviceAsync<PerformanceWatcher>().processUnfinishedFreeze { dir, duration ->
    val files = try {
      withContext(Dispatchers.IO) {
        Files.newDirectoryStream(dir).use { it.toList() }
      }
    }
    catch (ignore: IOException) {
      return@processUnfinishedFreeze
    }

    // report deadly freeze
    if (duration > FREEZE_THRESHOLD) {
      try {
        LifecycleUsageTriggerCollector.onDeadlockDetected()
        if (isEnabled(ApplicationManager.getApplication())) {
          reportDeadlocks(files = files, duration = duration, dir = dir)
        }
      }
      catch (e: IOException) {
        logger<IdeaFreezeReporter>().warn(e)
      }
    }
    cleanup(dir)
  }
}

private suspend fun reportDeadlocks(files: List<Path>, duration: Int, dir: Path) {
  val attachments = ArrayList<Attachment>()
  var message: String? = null
  var appInfo: String? = null
  var throwable: Throwable? = null
  val dumps = ArrayList<String>()
  for (file in files) {
    coroutineContext.ensureActive()
    val name = file.fileName.toString()

    suspend fun readText(): String {
      return withContext(Dispatchers.IO) {
        Files.readString(file)
      }
    }

    when {
      MESSAGE_FILE_NAME == name -> {
        message = readText()
      }
      THROWABLE_FILE_NAME == name -> {
        try {
          withContext(Dispatchers.IO) {
            ObjectInputStream(Files.newInputStream(file)).use { inputStream ->
              throwable = inputStream.readObject() as Throwable
            }
          }
        }
        catch (ignored: Exception) {
        }
      }
      APP_INFO_FILE_NAME == name -> {
        appInfo = readText()
      }
      name.startsWith(REPORT_PREFIX) -> {
        attachments.add(createReportAttachment(duration.toLong(), readText()))
      }
      name.startsWith(PerformanceWatcher.DUMP_PREFIX) -> {
        dumps.add(readText())
      }
    }
  }

  addDumpsAttachments(dumps, { it }, attachments)
  EP_NAME.forEachExtensionSafe { attachments.addAll(it.getAttachments(dir)) }
  if (message != null && throwable != null && !attachments.isEmpty()) {
    val event = LogMessage.eventOf(throwable!!, message, attachments)
    IdeaFreezeReporter.setAppInfo(event, appInfo)
    IdeaFreezeReporter.report(event)
  }
}

private fun isEnabled(app: Application): Boolean {
  return app.isEAP || app.isInternal || java.lang.Boolean.getBoolean("idea.force.freeze.reports")
}

private fun createReportAttachment(durationInSeconds: Long, text: String): Attachment {
  val result = Attachment("$REPORT_PREFIX-${durationInSeconds}s.txt", text)
  result.isIncluded = true
  return result
}

// get 20 scattered elements
private fun <T> addDumpsAttachments(from: List<T>, textMapper: (T) -> String, container: MutableList<Attachment>) {
  val size = from.size.coerceAtMost(20)
  val step = from.size / size
  for (i in 0 until size) {
    val attachment = Attachment("$DUMP_PREFIX-$i.txt", textMapper(from.get(i * step)))
    attachment.isIncluded = true
    container.add(attachment)
  }
}

private fun cleanup(dir: Path) {
  try {
    Files.deleteIfExists(dir.resolve(MESSAGE_FILE_NAME))
    Files.deleteIfExists(dir.resolve(THROWABLE_FILE_NAME))
    Files.deleteIfExists(dir.resolve(APP_INFO_FILE_NAME))
  }
  catch (ignore: IOException) {
  }
}

private fun getCauseThread(threadInfos: Array<ThreadInfo>): ThreadInfo? { // ensure sorted for better read action matching
  ThreadDumper.sort(threadInfos)
  val edt = threadInfos.find { ThreadDumper.isEDT(it) }
  if (edt == null || edt.threadState == Thread.State.RUNNABLE) {
    return edt
  }

  val id = edt.lockOwnerId
  if (id != -1L) {
    for (info in threadInfos) {
      if (info.threadId == id) {
        return info
      }
    }
  }

  val lockName = edt.lockName
  if (lockName != null && lockName.contains("ReadMostlyRWLock")) {
    var readLockNotRunnable: ThreadInfo? = null
    for (info in threadInfos) {
      if (isWithReadLock(info)) {
        if (info.threadState == Thread.State.RUNNABLE) {
          return info
        }

        if (readLockNotRunnable == null) {
          readLockNotRunnable = info
        }
      }
    }
    if (readLockNotRunnable != null) {
      return readLockNotRunnable
    }
  }
  return edt
}

private fun isWithReadLock(thread: ThreadInfo): Boolean {
  var read = false
  for (s in thread.stackTrace) {
    val methodName = s.methodName
    if (methodName == "runReadAction" || methodName == "tryRunReadAction" || methodName == "insideReadAction") {
      read = true
    }
    if (methodName == "waitABit") {
      return false
    }
  }
  return read
}

private fun skippedFrame(e: StackTraceElement): Boolean {
  return e.className == ApplicationImpl::class.java.name && e.methodName == "runEdtProgressWriteAction"
}

private fun countClassLoading(causeThreads: List<ThreadInfo>): Int {
  return causeThreads.count { threadInfo -> threadInfo.stackTrace.any { isClassLoading(it) } }
}

private fun isClassLoading(stackTraceElement: StackTraceElement): Boolean {
  return "loadClass" == stackTraceElement.methodName && "java.lang.ClassLoader" == stackTraceElement.className
}