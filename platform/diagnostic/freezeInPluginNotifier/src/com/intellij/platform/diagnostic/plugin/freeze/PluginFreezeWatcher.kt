package com.intellij.platform.diagnostic.plugin.freeze

import com.intellij.diagnostic.IdePerformanceListener
import com.intellij.diagnostic.ThreadDump
import com.intellij.ide.plugins.PluginUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.diagnostic.freezeAnalyzer.FreezeAnalyzer
import com.intellij.threadDumpParser.ThreadState
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@Service(Service.Level.APP)
internal class PluginFreezeWatcher : IdePerformanceListener, Disposable {
  var latestFrozenPlugin: PluginId? = null
  private val stackTracePattern = """at (\S+)\.(\S+)\(([^:]+):(\d+)\)""".toRegex()

  companion object {
    @JvmStatic
    fun getInstance(): PluginFreezeWatcher = service()
  }

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(IdePerformanceListener.TOPIC, this)
  }

  override fun dispose() {}

  override fun dumpedThreads(toFile: Path, dump: ThreadDump) {
    val freezeCausingThreads = FreezeAnalyzer.analyzeFreeze(dump.rawDump, null)?.threads.orEmpty()
    val pluginIds = freezeCausingThreads.mapNotNull { analyzeFreezeCausingPlugin(it) }
    latestFrozenPlugin = pluginIds.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    ProjectManager.getInstance().openProjects.firstOrNull()?.let { project ->
      FileEditorManager.getInstance(project).focusedEditor?.file?.let { file ->
        EditorNotifications.getInstance(project).updateNotifications(file)
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