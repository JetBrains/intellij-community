// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.diagnostic

import com.intellij.diagnostic.ITNProxy.appInfoString
import com.intellij.diagnostic.IdeErrorsDialog.Companion.getSubmitter
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtil
import com.intellij.internal.DebugAttachDetector
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SmartList
import com.intellij.util.concurrency.NonUrgentExecutor
import com.intellij.util.containers.ContainerUtil
import java.io.FileInputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.lang.management.ThreadInfo
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.math.max
import kotlin.math.min

internal class IdeaFreezeReporter : IdePerformanceListener {
  private var dumpTask: SamplingTask? = null
  private val currentDumps = ArrayList<ThreadDump>()
  private var stacktraceCommonPart: List<StackTraceElement>? = null

  @Volatile
  private var appClosing = false

  init {
    val app = ApplicationManager.getApplication()
    NonUrgentExecutor.getInstance().execute {
      app.messageBus.simpleConnect().subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
        override fun appWillBeClosed(isRestart: Boolean) {
          appClosing = true
        }
      })
      reportUnfinishedFreezes()
    }
    if (!DEBUG && PluginManagerCore.isRunningFromSources() || !isEnabled(app)) {
      throw ExtensionNotApplicableException.create()
    }
  }

  companion object {
    fun setAppInfo(event: IdeaLoggingEvent, appInfo: String?) {
      val data = event.data
      if (data is AbstractMessage) {
        data.appInfo = appInfo
      }
    }

    fun saveAppInfo(appInfoFile: Path, overwrite: Boolean) {
      if (overwrite || !Files.exists(appInfoFile)) {
        Files.createDirectories(appInfoFile.parent)
        Files.writeString(appInfoFile, appInfoString)
      }
    }

    fun report(event: IdeaLoggingEvent?) {
      if (event != null) {
        val t = event.throwable
        // only report to JB
        if (getSubmitter(t, PluginUtil.getInstance().findPluginId(t)) is ITNReporter) {
          MessagePool.getInstance().addIdeFatalMessage(event)
        }
      }
    }
  }

  override fun uiFreezeStarted(reportDir: Path) {
    if (DEBUG || !DebugAttachDetector.isAttached()) {
      dumpTask?.stop()

      reset()
      val watcher = PerformanceWatcher.getInstance()
      val maxDumpDuration = watcher.maxDumpDuration
      if (maxDumpDuration == 0) {
        return
      }

      dumpTask = object : SamplingTask(100, maxDumpDuration) {
        override fun stop() {
          super.stop()
          EP_NAME.forEachExtensionSafe(FreezeProfiler::stop)
        }
      }
      EP_NAME.forEachExtensionSafe { it.start(reportDir) }
    }
  }

  override fun dumpedThreads(toFile: Path, dump: ThreadDump) {
    if (dumpTask == null) {
      return
    }

    currentDumps.add(dump)
    val edtStack = dump.edtStackTrace
    if (edtStack != null) {
      stacktraceCommonPart = if (stacktraceCommonPart == null) {
        edtStack.toList()
      }
      else {
        getStacktraceCommonPart(stacktraceCommonPart!!, edtStack)
      }
    }
    val dir = toFile.parent
    val performanceWatcher = PerformanceWatcher.getInstance()
    val event = createEvent(duration = dumpTask!!.totalTime + performanceWatcher.unresponsiveInterval, attachments = emptyList(),
                            reportDir = dir,
                            performanceWatcher = performanceWatcher,
                            finished = false)
    if (event != null) {
      try {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve(MESSAGE_FILE_NAME), event.message)
        ObjectOutputStream(Files.newOutputStream(dir.resolve(THROWABLE_FILE_NAME))).use { it.writeObject(event.throwable) }
        saveAppInfo(dir.resolve(APP_INFO_FILE_NAME), false)
      }
      catch (ignored: IOException) {
      }
    }
  }

  override fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {
    (dumpTask ?: return).stop()
    reportDir?.let(::cleanup)
  }

  override fun uiFreezeRecorded(durationMs: Long, reportDir: Path?) {
    if (dumpTask == null) {
      return
    }

    if (Registry.`is`("freeze.reporter.enabled", false)) {
      val performanceWatcher = PerformanceWatcher.getInstance()
      if ((durationMs / 1000).toInt() > FREEZE_THRESHOLD && !stacktraceCommonPart.isNullOrEmpty()) {
        // check that we have at least half of the dumps required
        val dumpingDurationMs = durationMs - performanceWatcher.unresponsiveInterval
        val dumpsCount = min(performanceWatcher.maxDumpDuration.toLong(), dumpingDurationMs / 2) / performanceWatcher.dumpInterval
        if (dumpTask!!.isValid(dumpingDurationMs) || currentDumps.size >= max(3, dumpsCount)) {
          val attachments = ArrayList<Attachment>()
          addDumpsAttachments(from = currentDumps, textMapper = { it.rawDump }, container = attachments)
          if (reportDir != null) {
            EP_NAME.forEachExtensionSafe { attachments.addAll(it.getAttachments(reportDir)) }
          }
          report(createEvent(duration = durationMs,
                             attachments = attachments,
                             reportDir = reportDir,
                             performanceWatcher = performanceWatcher,
                             finished = true))
        }
      }
    }

    dumpTask = null
    reset()
  }

  private fun reset() {
    currentDumps.clear()
    stacktraceCommonPart = null
  }

  private fun createEvent(duration: Long,
                          attachments: List<Attachment>,
                          reportDir: Path?,
                          performanceWatcher: PerformanceWatcher,
                          finished: Boolean): IdeaLoggingEvent? {
    var infos = dumpTask!!.threadInfos
    val dumpInterval = (if (infos.isEmpty()) performanceWatcher.dumpInterval else dumpTask!!.dumpInterval).toLong()
    if (infos.isEmpty()) {
      infos = currentDumps.map { it.threadInfos }
    }

    return createEvent(duration = duration,
                       dumpInterval = dumpInterval,
                       sampledCount = infos.size,
                       causeThreads = infos.mapNotNull { getCauseThread(it) },
                       attachments = attachments,
                       reportDir = reportDir,
                       jitProblem = performanceWatcher.jitProblem,
                       finished = finished)
  }

  private fun createEvent(duration: Long,
                          dumpInterval: Long,
                          sampledCount: Int,
                          causeThreads: List<ThreadInfo>,
                          attachments: List<Attachment>,
                          reportDir: Path?,
                          jitProblem: String?,
                          finished: Boolean): IdeaLoggingEvent? {
    val allInEdt = causeThreads.all { ThreadDumper.isEDT(it) }
    val root = CallTreeNode.buildTree(causeThreads, dumpInterval)
    val classLoadingRatio = countClassLoading(causeThreads) * 100 / causeThreads.size
    val commonStackNode = root.findDominantCommonStack((causeThreads.size * dumpInterval * COMMON_SUB_STACK_WEIGHT).toLong())
    var commonStack = commonStackNode?.getStack()
    var nonEdtCause = false

    if (commonStack.isNullOrEmpty()) {
      // fallback to simple EDT common
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

    if (!commonStack.isNullOrEmpty()) {
      if (commonStack.any { skippedFrame(it) }) {
        return null
      }
      val durationInSeconds = duration / 1000
      val edtNote = if (allInEdt) "in EDT " else ""
      var message = """Freeze ${edtNote}for $durationInSeconds seconds
${if (finished) "" else if (appClosing) "IDE is closing. " else "IDE KILLED! "}Sampled time: ${sampledCount * dumpInterval}ms, sampling rate: ${dumpInterval}ms"""
      if (jitProblem != null) {
        message += ", $jitProblem"
      }
      val total = dumpTask!!.totalTime
      val gcTime = dumpTask!!.gcTime
      if (total > 0) {
        message += ", GC time: ${gcTime}ms (${gcTime * 100 / total}%), Class loading: $classLoadingRatio%"
      }
      if (DebugAttachDetector.isDebugEnabled()) {
        message += ", debug agent: on"
      }
      val processCpuLoad = dumpTask!!.processCpuLoad
      if (processCpuLoad > 0) {
        message += ", cpu load: " + (processCpuLoad * 100).toInt() + "%"
      }
      if (nonEdtCause) {
        message += "\n\nThe stack is from the thread that was blocking EDT"
      }
      val report = createReportAttachment(durationInSeconds, reportText)
      return LogMessage.eventOf(Freeze(commonStack), message, ContainerUtil.append(attachments, report))
    }
    return null
  }
}

private class CallTreeNode private constructor(private val stackTraceElement: StackTraceElement?,
                                               private val parent: CallTreeNode?,
                                               private var time: Long,
                                               val threadInfo: ThreadInfo?) {
  private val children = SmartList<CallTreeNode>()
  private val depth: Int = if (parent == null) 0 else parent.depth + 1

  companion object {
    @JvmField
    val TIME_COMPARATOR: Comparator<CallTreeNode> = Comparator.comparingLong<CallTreeNode> { it.time }.reversed()

    fun buildTree(threadInfos: List<ThreadInfo>, time: Long): CallTreeNode {
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
  }

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
    builder.append(stackTraceElement!!.className).append(".").append(stackTraceElement.methodName)
      .append(" ").append(time).append("ms").append('\n')
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

  fun findDominantCommonStack(threshold: Long): CallTreeNode? {
    // find dominant
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


private val EP_NAME = ExtensionPointName<FreezeProfiler>("com.intellij.diagnostic.freezeProfiler")

// intentionally hardcoded and not implemented via a registry key or system property
// to be updated when we ready to collect freezes from the specified duration and up
private const val FREEZE_THRESHOLD = 10
private const val REPORT_PREFIX = "report"
private const val DUMP_PREFIX = "dump"
private const val MESSAGE_FILE_NAME = ".message"
private const val THROWABLE_FILE_NAME = ".throwable"
@Suppress("SpellCheckingInspection")
internal const val APP_INFO_FILE_NAME = ".appinfo"

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

private fun reportUnfinishedFreezes() {
  if (!DEBUG && PluginManagerCore.isRunningFromSources()) {
    return
  }

  val app = ApplicationManager.getApplication()
  PerformanceWatcher.getInstance().processUnfinishedFreeze { dir: Path, duration: Int ->
    try {
      // report deadly freeze
      val files = dir.toFile().listFiles() ?: return@processUnfinishedFreeze
      if (duration > FREEZE_THRESHOLD) {
        LifecycleUsageTriggerCollector.onDeadlockDetected()
        if (isEnabled(app)) {
          val attachments = ArrayList<Attachment>()
          var message: String? = null
          var appInfo: String? = null
          var throwable: Throwable? = null
          val dumps = ArrayList<String>()
          for (file in files) {
            val text = FileUtil.loadFile(file)
            val name = file.name
            if (MESSAGE_FILE_NAME == name) {
              message = text
            }
            else if (THROWABLE_FILE_NAME == name) {
              try {
                FileInputStream(file).use { fis -> ObjectInputStream(fis).use { ois -> throwable = ois.readObject() as Throwable } }
              }
              catch (ignored: Exception) {
              }
            }
            else if (APP_INFO_FILE_NAME == name) {
              appInfo = text
            }
            else if (name.startsWith(REPORT_PREFIX)) {
              attachments.add(createReportAttachment(duration.toLong(), text))
            }
            else if (name.startsWith(PerformanceWatcher.DUMP_PREFIX)) {
              dumps.add(text)
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
      }
      cleanup(dir)
    }
    catch (ignored: IOException) {
    }
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

private fun getCauseThread(threadInfos: Array<ThreadInfo>): ThreadInfo? {
  // ensure sorted for better read action matching
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