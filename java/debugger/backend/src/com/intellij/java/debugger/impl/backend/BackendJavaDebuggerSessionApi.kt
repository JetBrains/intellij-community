// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.ide.ui.icons.rpcId
import com.intellij.java.debugger.impl.shared.rpc.*
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.DumpItem
import com.intellij.unscramble.MergeableDumpItem
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.rpc.core.util.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce

internal class BackendJavaDebuggerSessionApi : JavaDebuggerSessionApi {
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun dumpThreads(sessionId: XDebugSessionId): JavaThreadDumpResponseDto? {
    val xSession = sessionId.findValue() ?: return null

    val javaDebugProcess = xSession.debugProcess as JavaDebugProcess
    val session = javaDebugProcess.debuggerSession
    val context = session.contextManager.context
    if (session == null || !session.isAttached) {
      return null
    }
    val channelDeferred = CompletableDeferred<ReceiveChannel<List<MergeableDumpItem>>>()
    executeOnDMT(context.managerThread!!) {
      // Pass parts of the dump to the ThreadDumpPanel via a channel as soon as they are computed
      val dumpItemsChannel = produce(capacity = Channel.BUFFERED) {
        ThreadDumpAction.buildThreadDump(context, channel)
      }
      channelDeferred.complete(dumpItemsChannel)
    }
    val dtos = channelDeferred.await().map { mergeableItem ->
      val threadDumpDto = mergeableItem.dumpItemDtos()
      val mergedThreadDumpDto = CompoundDumpItem.mergeThreadDumpItems(mergeableItem).dumpItemDtos()
      JavaThreadDumpDto(threadDumpDto, mergedThreadDumpDto)
    }
    return JavaThreadDumpResponseDto(dtos, ExceptionFilters.getFilters(session.searchScope))
  }
}

private fun List<DumpItem>.dumpItemDtos(): ThreadDumpWithAwaitingDependencies {
  val items = map {
    JavaThreadDumpItemDto(it.name, it.stateDesc, it.stackTrace, it.interestLevel, it.icon.rpcId(), it.attributes.toRpc(), it.isDeadLocked)
  }
  val awaiting = hashMapOf<Int, List<Int>>()
  val itemToIndex = this.withIndex().associate { it.value to it.index }
  for ((item, index) in itemToIndex) {
    val awaitingIndices = item.awaitingDumpItems.mapNotNull { itemToIndex[it] }
    if (awaitingIndices.isNotEmpty()) {
      awaiting[index] = awaitingIndices
    }
  }
  return ThreadDumpWithAwaitingDependencies(items, awaiting)
}
