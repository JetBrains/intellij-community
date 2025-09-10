// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions

import com.intellij.execution.filters.Filter
import com.intellij.frontend.FrontendApplicationInfo
import com.intellij.frontend.FrontendType
import com.intellij.ide.ui.icons.icon
import com.intellij.java.debugger.impl.shared.SharedDebuggerUtils
import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpDto
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpItemDto
import com.intellij.java.debugger.impl.shared.rpc.ThreadDumpWithAwaitingDependencies
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.debugger.impl.rpc.toSimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes
import com.intellij.unscramble.DumpItem
import com.intellij.util.BitUtil
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import fleet.rpc.core.util.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import javax.swing.Icon

private class ThreadDumpAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun actionPerformed(e: AnActionEvent) {
    val onlyPlatformThreads = BitUtil.isSet(e.modifiers, InputEvent.ALT_MASK)

    val project = e.project
    if (project == null) {
      return
    }
    val sessionProxy = DebuggerUIUtil.getSessionProxy(e) ?: return
    sessionProxy.coroutineScope.launch {
      val dtosWillBeSerialized = FrontendApplicationInfo.getFrontendType() !is FrontendType.Monolith
      val maxItems = if (dtosWillBeSerialized) Registry.intValue("debugger.thread.dump.max.items.frontend") else Int.MAX_VALUE
      val (threadDumpsDtoChannel, filters) = JavaDebuggerSessionApi.getInstance().dumpThreads(sessionProxy.id, maxItems, onlyPlatformThreads) ?: return@launch
      val threadDumpsChannel = threadDumpsDtoChannel.map(::threadDumpData)

      withContext(Dispatchers.EDT) {
        collectAndShowDumpItems(project, sessionProxy, threadDumpsChannel, filters)
      }
    }
  }

  override fun update(e: AnActionEvent) {
    val javaSession = SharedJavaDebuggerSession.findSession(e)
    val isAttached = javaSession != null && javaSession.isAttached
    e.presentation.setEnabled(isAttached)
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

  for (dump in threadDumpsChannel) {
    threadDumpPanel.addDumpItems(dump.threadDump, dump.threadDumpTruncatedSize,
                                 dump.mergedThreadDump, dump.mergedThreadDumpTruncatedSize)
  }
}

private data class ThreadDumpData(
  val threadDump: List<DumpItem>,
  val threadDumpTruncatedSize: Int,
  val mergedThreadDump: List<DumpItem>,
  val mergedThreadDumpTruncatedSize: Int,
)

private fun threadDumpData(dto: JavaThreadDumpDto): ThreadDumpData {
  return ThreadDumpData(
    dto.threadDump.toDumpItems(),
    dto.threadDump.truncatedItemsNumber,
    dto.mergedThreadDump.toDumpItems(),
    dto.mergedThreadDump.truncatedItemsNumber,
  )
}

private fun ThreadDumpWithAwaitingDependencies.toDumpItems(): List<DumpItem> {
  val iconsCache = icons.map { it.icon() }
  val attributesCache = attributes.map { it.toSimpleTextAttributes() }

  val feDumpItems = items.map { FrontendDumpItem(it, iconsCache, attributesCache, stackTraces, stateDescriptions, iconToolTips) }
  for ((index, awaitingIndices) in awaitingDependencies) {
    val awaitingItems = awaitingIndices.map { feDumpItems[it] }.toHashSet()
    feDumpItems[index].setAwaitingItems(awaitingItems)
  }
  return feDumpItems
}

private class FrontendDumpItem(
  private val itemDto: JavaThreadDumpItemDto,
  private val iconsCache: List<Icon>,
  private val attributesCache: List<SimpleTextAttributes>,
  private val stackTracesCache: List<@NlsSafe String>,
  private val stateDescriptionsCache: List<@NlsSafe String>,
  private val iconToolTipsCache: List<@Nls String?>,
) : DumpItem {
  private var internalAwaitingItems: Set<DumpItem> = emptySet()

  override val name: @NlsSafe String get() = itemDto.name
  override val stateDesc: @NlsSafe String get() = stateDescriptionsCache[itemDto.stateDescriptionIndex]
  override val stackTrace: @NlsSafe String get() = "${itemDto.firstLine}\n${stackTracesCache[itemDto.stackTraceIndex]}"
  override val iconToolTip: @Nls String? get() = iconToolTipsCache[itemDto.iconToolTipIndex.toUInt().toInt()]
  override val interestLevel: Int get() = itemDto.interestLevel
  override val icon: Icon get() = iconsCache[itemDto.iconIndex.toUInt().toInt()]
  override val attributes: SimpleTextAttributes get() = attributesCache[itemDto.attributesIndex.toInt().toUInt().toInt()]
  override val isDeadLocked: Boolean get() = itemDto.isDeadLocked
  override val awaitingDumpItems: Set<DumpItem> get() = internalAwaitingItems

  fun setAwaitingItems(items: Set<DumpItem>) {
    internalAwaitingItems = items
  }
}
