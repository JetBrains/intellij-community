package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.diagnostic.IdePerformanceListener
import com.intellij.diagnostic.ThreadDump
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.plugins.PluginUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.diagnostic.freezeAnalyzer.FreezeAnalyzer
import com.intellij.threadDumpParser.ThreadState
import com.intellij.ui.EditorNotifications
import java.nio.file.Path
import kotlin.io.path.absolutePathString

@Service(Service.Level.APP)
internal class PluginFreezeWatcher : IdePerformanceListener, Disposable {
  @Volatile
  private var latestFrozenPlugin: PluginId? = null
  @Volatile
  private var lastFreezeDuration: Long = -1

  private val stackTracePattern: Regex = """at (\S+)\.(\S+)\(([^:]+):(\d+)\)""".toRegex()

  companion object {
    @JvmStatic
    fun getInstance(): PluginFreezeWatcher = service()
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(IdePerformanceListener.TOPIC, this)
  }

  fun getFreezeReason(): PluginId? = latestFrozenPlugin

  fun reset() {
    latestFrozenPlugin = null
    lastFreezeDuration = -1
  }

  override fun dispose() {}

  override fun uiFreezeStarted(reportDir: Path) {
    if (latestFrozenPlugin != null) return

    lastFreezeDuration = -1
  }

  override fun uiFreezeFinished(durationMs: Long, reportDir: Path?) {
    if (lastFreezeDuration < 0) {
      lastFreezeDuration = durationMs

      reportCounters()
    }
  }

  override fun dumpedThreads(toFile: Path, dump: ThreadDump) {
    if (latestFrozenPlugin != null) return // user have not yet handled previous failure

    val freezeCausingThreads = FreezeAnalyzer.analyzeFreeze(dump.rawDump, null)?.threads.orEmpty()
    val pluginIds = freezeCausingThreads.mapNotNull { analyzeFreezeCausingPlugin(it) }
    val frozenPlugin = pluginIds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return

    val freezeStorageService = PluginsFreezesService.getInstance()
    if (freezeStorageService.shouldBeIgnored(frozenPlugin)) return
    freezeStorageService.setLatestFreezeDate(frozenPlugin)

    latestFrozenPlugin = frozenPlugin

    Logger.getInstance(PluginFreezeWatcher::class.java)
      .warn("Plugin '$frozenPlugin' caused IDE freeze." +
            "Find thread dumps at ${toFile.absolutePathString()}")

    reportCounters()

    ReadAction.compute<Unit, Throwable> {
      for (project in ProjectManager.getInstance().openProjects) {
        EditorNotifications.getInstance(project).updateAllNotifications()
      }
    }
  }

  private fun reportCounters() {
    latestFrozenPlugin?.let {
      if (lastFreezeDuration > 0) {
        LifecycleUsageTriggerCollector.pluginFreezeReported(it, lastFreezeDuration)
      }
    }
  }

  private fun analyzeFreezeCausingPlugin(threadInfo: ThreadState): PluginId? {
    val stackTraceElements = threadInfo.stackTrace.lineSequence()
      .mapNotNull { parseStackTraceElement(it) }
      .toList()
      .toTypedArray()

    return PluginUtilImpl.doFindPluginId(Throwable().apply { stackTrace = stackTraceElements })
  }

  private fun parseStackTraceElement(stackTrace: String): StackTraceElement? {
    return stackTracePattern.find(stackTrace.trim())?.let { matchResult ->
      val (className, methodName, fileName, lineNumber) = matchResult.destructured
      StackTraceElement(className, methodName, fileName, lineNumber.toInt())
    }
  }
}