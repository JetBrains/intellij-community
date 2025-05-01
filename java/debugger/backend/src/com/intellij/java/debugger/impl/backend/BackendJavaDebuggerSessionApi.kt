// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.ide.ui.icons.rpcId
import com.intellij.java.debugger.impl.shared.rpc.*
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.DumpItem
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.toRpc
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
  val attributes = dumpItems.map { it.attributes }.distinct()
  val attributesToIndex = attributes.withIndex().associate { it.value to it.index.toByte() }
  val icons = dumpItems.map { it.icon }.distinct()
  val iconToIndex = icons.withIndex().associate { it.value to it.index.toByte() }
  val stateDescriptions = dumpItems.map { it.stateDesc }.distinct()
  val stateDescriptionsToIndex = stateDescriptions.withIndex().associate { it.value to it.index }

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
                          stateDescriptionIndex = stateDescriptionsToIndex[it.stateDesc]!!,
                          interestLevel = it.interestLevel,
                          iconIndex = iconToIndex[it.icon]!!,
                          attributesIndex = attributesToIndex[it.attributes]!!,
                          isDeadLocked = it.isDeadLocked,
                          stackTraceIndex = stackTraceIndex,
                          firstLine = firstLine)
  }

  return ThreadDumpWithAwaitingDependencies(items = items,
                                            icons = icons.map { it.rpcId() },
                                            attributes = attributes.map { it.toRpc() },
                                            stackTraces = stackTraces,
                                            awaitingDependencies = awaiting,
                                            stateDescriptions = stateDescriptions,
                                            truncatedItemsNumber = truncatedSize)
}

private data class StackTraceWithSeparatedFirstLine(val firstLine: String, val stackTrace: String, val dumpItem: DumpItem)
private data class DumpItemWithStackTraceIndex(val firstLine: String, val stackTraceIndex: Int)