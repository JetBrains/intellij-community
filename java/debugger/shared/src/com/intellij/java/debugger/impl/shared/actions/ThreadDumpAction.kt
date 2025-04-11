// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.execution.filters.Filter
import com.intellij.ide.ui.icons.icon
import com.intellij.java.debugger.impl.shared.SharedDebuggerUtils
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpDto
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpItemDto
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import com.intellij.unscramble.DumpItem
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.toSimpleTextAttributes
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import fleet.rpc.core.util.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Color
import javax.swing.Icon

private class ThreadDumpAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      return
    }
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return
    sessionProxy.coroutineScope.launch {
      val (threadDumpsDtoChannel, filters) = JavaDebuggerSessionApi.getInstance().dumpThreads(sessionProxy.id) ?: return@launch
      val threadDumpsChannel = threadDumpsDtoChannel.map { it.threadDumpData() }

      withContext(Dispatchers.EDT) {
        collectAndShowDumpItems(project, sessionProxy, threadDumpsChannel, filters)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    if (project == null) {
      presentation.setEnabled(false)
      return
    }
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e)
    if (sessionProxy == null) {
      presentation.setEnabled(false)
      return
    }
    // TODO should be isAttached
    val isAttached = !sessionProxy.isStopped
    presentation.setEnabled(isAttached)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private suspend fun collectAndShowDumpItems(
  project: Project,
  session: XDebugSessionProxy,
  threadDumpsChannel: ReceiveChannel<ThreadDumpData>,
  filters: List<Filter>,
) {
  val ui = session.sessionTab?.ui ?: return
  val threadDumpPanel = SharedDebuggerUtils.createThreadDumpPanel(project, ui, filters)

  for ((threadDump, mergedThreadDump) in threadDumpsChannel) {
    threadDumpPanel.addDumpItems(threadDump, mergedThreadDump)
  }
}

private data class ThreadDumpData(val threadDump: List<DumpItem>, val mergedThreadDump: List<DumpItem>)

private fun JavaThreadDumpDto.threadDumpData(): ThreadDumpData {
  return ThreadDumpData(threadDump.toDumpItems(), mergedThreadDump.toDumpItems())
}

private fun List<JavaThreadDumpItemDto>.toDumpItems(): List<DumpItem> = map { itemDto ->
  object : DumpItem {
    override val name: @NlsSafe String = itemDto.name
    override val stateDesc: @NlsSafe String = itemDto.stateDesc
    override val stackTrace: @NlsSafe String = itemDto.stackTrace
    override val interestLevel: Int = itemDto.interestLevel
    override val icon: Icon = itemDto.iconId.icon()
    override val attributes: SimpleTextAttributes = itemDto.attributes.toSimpleTextAttributes()

    // TODO pass correct color here
    override fun getBackgroundColor(selectedItem: DumpItem?): Color? = UIUtil.getListBackground()
  }
}
