// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.actions.FreezeThreadAction
import com.intellij.debugger.actions.InterruptThreadAction
import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.ide.ui.icons.rpcId
import com.intellij.debugger.actions.ResumeThreadAction
import com.intellij.java.debugger.impl.shared.rpc.*
import com.intellij.platform.debugger.impl.rpc.toRpc
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.DumpItem
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.XExecutionStackId
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.util.channels.use
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch

internal class BackendJavaDebuggerSessionApi : JavaDebuggerSessionApi {
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun dumpThreads(sessionId: XDebugSessionId, maxItems: Int, onlyPlatformThreads: Boolean): JavaThreadDumpResponseDto? {
    val xSession = sessionId.findValue() ?: return null

    val javaDebugProcess = xSession.debugProcess as JavaDebugProcess
    val session = javaDebugProcess.debuggerSession
    val context = session.contextManager.context
    if (session == null || !session.isAttached) {
      return null
    }
    val channelDeferred = CompletableDeferred<ReceiveChannel<JavaThreadDumpDto>>()
    executeOnDMT(context.managerThread!!) {
      // Pass parts of the dump to the ThreadDumpPanel via a channel as soon as they are computed
      val dumpItemsChannel = produce(capacity = Channel.BUFFERED) {
        ThreadDumpAction.buildThreadDump(context, onlyPlatformThreads, channel)
      }
      val dtosChannel = Channel<JavaThreadDumpDto>(capacity = Channel.BUFFERED)
      launch(Dispatchers.Default) {
        dtosChannel.use {
          for (mergeableItem in dumpItemsChannel) {
            val threadDumpDto = dumpItemDtos(mergeableItem, maxItems)
            val mergedThreadDumpDto = dumpItemDtos(CompoundDumpItem.mergeThreadDumpItems(mergeableItem), maxItems)
            dtosChannel.send(JavaThreadDumpDto(threadDumpDto, mergedThreadDumpDto))
          }
        }
      }
      channelDeferred.complete(dtosChannel)
    }
    return JavaThreadDumpResponseDto(channelDeferred.await(), ExceptionFilters.getFilters(session.searchScope))
  }

  override suspend fun setAsyncStacksEnabled(sessionId: XDebugSessionId, state: Boolean) {
    val session = sessionId.findValue() ?: return
    AsyncStacksUtils.setAsyncStacksEnabled(session, state)
  }

  override suspend fun resumeThread(executionStackId: XExecutionStackId) {
    invokeThreadCommand(executionStackId, ThreadCommand.RESUME)
  }

  override suspend fun freezeThread(executionStackId: XExecutionStackId) {
    invokeThreadCommand(executionStackId, ThreadCommand.FREEZE)
  }

  override suspend fun interruptThread(executionStackId: XExecutionStackId) {
    invokeThreadCommand(executionStackId, ThreadCommand.INTERRUPT)
  }

  private fun invokeThreadCommand(executionStackId: XExecutionStackId, command: ThreadCommand) {
    val executionStackModel = executionStackId.findValue() ?: return
    val xSession = executionStackModel.session
    val javaDebugProcess = xSession.debugProcess as JavaDebugProcess
    val session = javaDebugProcess.debuggerSession
    val context = session.contextManager.context
    val debugProcess = context.debugProcess!!
    if (session == null || !session.isAttached) return

    val executionStack = (executionStackModel.executionStack as? JavaExecutionStack) ?: return
    val threadProxy = executionStack.threadProxy
    val managerThread = context.managerThread ?: return
    when(command) {
      ThreadCommand.RESUME -> {
        ResumeThreadAction.resumeThread(threadProxy, debugProcess, managerThread)
      }
      ThreadCommand.FREEZE -> {
        FreezeThreadAction.freezeThread(threadProxy, debugProcess, managerThread)
      }
      ThreadCommand.INTERRUPT -> {
        InterruptThreadAction.interruptThread(threadProxy, debugProcess, managerThread)
      }
    }
  }

  companion object {
    private enum class ThreadCommand { FREEZE, RESUME, INTERRUPT }
  }
}

private fun truncateIfNeeded(allDumpItems: List<DumpItem>, maxItems: Int): Pair<List<DumpItem>, Int> {
  return if (allDumpItems.size <= maxItems) {
    allDumpItems to 0
  }
  else {
    allDumpItems.take(maxItems) to allDumpItems.size - maxItems
  }
}

private fun dumpItemDtos(allDumpItems: List<DumpItem>, maxItems: Int): ThreadDumpWithAwaitingDependencies {
  val (dumpItems, truncatedSize) = truncateIfNeeded(allDumpItems, maxItems)

  fun <T> prepareIndex(selector: (DumpItem) -> T): Pair<List<T>, Map<T, Int>> {
    val values = dumpItems.map(selector).distinct()
    val valueToIndex = values.withIndex().associate { it.value to it.index }
    return Pair(values, valueToIndex)
  }

  val (attributes, attributesToIndex) = prepareIndex { it.attributes }
  val (icons, iconToIndex) = prepareIndex { it.icon }
  val (stateDescriptions, stateDescriptionToIndex) = prepareIndex { it.stateDesc }
  val (iconToolTips, iconToolTipToIndex) = prepareIndex { it.iconToolTip }

  val awaiting = hashMapOf<Int, IntArray>()
  val itemToIndex = dumpItems.withIndex().associate { it.value to it.index }
  for ((item, index) in itemToIndex) {
    val awaitingIndices = item.awaitingDumpItems.mapNotNull { itemToIndex[it] }
    if (awaitingIndices.isNotEmpty()) {
      awaiting[index] = awaitingIndices.toIntArray()
    }
  }

  val stackTraceToItems = dumpItems.map {
    val stackTrace = it.stackTrace
    val firstLineIndex = stackTrace.indexOf('\n')
    if (firstLineIndex >= 0) {
      val firstLine = stackTrace.take(firstLineIndex)
      val stackTraceWithoutFirstLine = stackTrace.substring(firstLineIndex + 1)
      StackTraceWithSeparatedFirstLine(firstLine, stackTraceWithoutFirstLine, it)
    }
    else {
      StackTraceWithSeparatedFirstLine(stackTrace, "", it)
    }
  }.groupBy { it.stackTrace }.toList()

  val stackTraces = stackTraceToItems.map { it.first }
  val itemToStackTrace = stackTraceToItems.withIndex().flatMap { (index, indexedValue) ->
    indexedValue.second.map { (firstLine, _, item) ->
      item to DumpItemWithStackTraceIndex(firstLine, index)
    }
  }.associate { it.first to it.second }

  val items = dumpItems.map {
    val (firstLine, stackTraceIndex) = itemToStackTrace[it]!!
    JavaThreadDumpItemDto(name = it.name,
                          stateDescriptionIndex = stateDescriptionToIndex[it.stateDesc]!!,
                          interestLevel = it.interestLevel,
                          iconIndex = iconToIndex[it.icon]!!.toByte(),
                          attributesIndex = attributesToIndex[it.attributes]!!.toByte(),
                          isDeadLocked = it.isDeadLocked,
                          stackTraceIndex = stackTraceIndex,
                          iconToolTipIndex = iconToolTipToIndex[it.iconToolTip]!!.toByte(),
                          firstLine = firstLine)
  }

  return ThreadDumpWithAwaitingDependencies(items = items,
                                            icons = icons.map { it.rpcId() },
                                            attributes = attributes.map { it.toRpc() },
                                            stackTraces = stackTraces,
                                            awaitingDependencies = awaiting,
                                            stateDescriptions = stateDescriptions,
                                            iconToolTips = iconToolTips,
                                            truncatedItemsNumber = truncatedSize)
}

private data class StackTraceWithSeparatedFirstLine(val firstLine: String, val stackTrace: String, val dumpItem: DumpItem)
private data class DumpItemWithStackTraceIndex(val firstLine: String, val stackTraceIndex: Int)