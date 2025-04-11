// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.withDebugContext
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.ide.ui.icons.rpcId
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpDto
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpItemDto
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpResponseDto
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.DumpItem
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.toRpc
import fleet.rpc.core.util.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope

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
    val dumpItemsChannel = withDebugContext(context.managerThread!!) {
      // Pass parts of the dump to the ThreadDumpPanel via a channel as soon as they are computed
      produce(capacity = Channel.BUFFERED) {
        ThreadDumpAction.buildThreadDump(context, channel)
      }
    }
    val dtos = dumpItemsChannel.map { mergeableItem ->
      val threadDumpDto = mergeableItem.dumpItemDtos()
      val mergedThreadDumpDto = CompoundDumpItem.mergeThreadDumpItems(mergeableItem).dumpItemDtos()
      JavaThreadDumpDto(threadDumpDto, mergedThreadDumpDto)
    }
    return JavaThreadDumpResponseDto(dtos, ExceptionFilters.getFilters(session.searchScope))
  }
}

@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun <T> emptyChannel(): ReceiveChannel<T> = coroutineScope { produce {} }


private fun List<DumpItem>.dumpItemDtos(): List<JavaThreadDumpItemDto> = map {
  JavaThreadDumpItemDto(it.name, it.stateDesc, it.stackTrace, it.interestLevel, it.icon.rpcId(), it.attributes.toRpc())
}
