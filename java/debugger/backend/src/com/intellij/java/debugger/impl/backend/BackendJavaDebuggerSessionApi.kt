// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.backend

import com.intellij.debugger.actions.FreezeThreadAction
import com.intellij.debugger.actions.InterruptThreadAction
import com.intellij.debugger.actions.MuteRendererUtils
import com.intellij.debugger.actions.ResumeThreadAction
import com.intellij.debugger.actions.StepOutOfBlockActionUtils
import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.AsyncStacksUtils
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.JavaExecutionStack
import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.executeOnDMT
import com.intellij.debugger.engine.withDebugContext
import com.intellij.debugger.settings.NodeRendererSettings
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.java.debugger.impl.shared.engine.NodeRendererId
import com.intellij.java.debugger.impl.shared.rpc.JavaDebuggerSessionApi
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpDto
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpItemDto
import com.intellij.java.debugger.impl.shared.rpc.JavaThreadDumpResponseDto
import com.intellij.java.debugger.impl.shared.rpc.ThreadDumpWithAwaitingDependencies
import com.intellij.openapi.application.EDT
import com.intellij.platform.debugger.impl.rpc.XDebugSessionId
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.DumpItem
import com.intellij.unscramble.splitFirstLineAndBody
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.findValue
import fleet.util.channels.use
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

  override suspend fun stepOutOfCodeBlock(sessionId: XDebugSessionId) {
    val xSession = sessionId.findValue() ?: return
    withContext(Dispatchers.EDT) {
      StepOutOfBlockActionUtils.stepOutOfBlock(xSession)
    }
  }

  override suspend fun setRenderer(rendererId: NodeRendererId?, xValueIds: List<XValueId>) {
    val xValueModels = xValueIds.mapNotNull { BackendXValueModel.findById(it) }
    val javaValues = xValueModels.mapNotNull { it.xValue as? JavaValue }
    if (javaValues.isEmpty()) return
    val renderer = if (rendererId != null) {
      javaValues[0].evaluationContext.debugProcess.getRendererById(rendererId) ?: return
    }
    else {
      null
    }

    for (javaValue in javaValues) {
      withDebugContext(javaValue.evaluationContext.suspendContext) {
        javaValue.setRenderer(renderer)
      }
    }
    for (xValueModel in xValueModels) {
      xValueModel.computeValuePresentation()
    }
  }

  override suspend fun muteRenderers(sessionId: XDebugSessionId, state: Boolean) {
    val xSession = sessionId.findValue() ?: return
    val renderersFlow = MuteRendererUtils.getOrCreateFlow(xSession.sessionData)
    renderersFlow.value = state
    NodeRendererSettings.getInstance().fireRenderersChanged()
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

  val (stackTraceBodies, itemToStackTrace) = deduplicateTextBodies(dumpItems) { it.stackTrace }
  val (exportedStackTraceBodies, itemToExportedStackTrace) = deduplicateTextBodies(dumpItems) { it.serialize() }

  val items = dumpItems.map {
    val (firstLine, stackTraceBodyIndex) = itemToStackTrace[it]!!
    val (exportedFirstLine, exportedStackTraceBodyIndex) = itemToExportedStackTrace[it]!!
    JavaThreadDumpItemDto(name = it.name,
                          stateDescriptionIndex = stateDescriptionToIndex[it.stateDesc]!!,
                          interestLevel = it.interestLevel,
                          iconIndex = iconToIndex[it.icon]!!.toByte(),
                          attributesIndex = attributesToIndex[it.attributes]!!.toByte(),
                          isDeadLocked = it.isDeadLocked,
                          stackTraceBodyIndex = stackTraceBodyIndex,
                          exportedStackTraceBodyIndex = exportedStackTraceBodyIndex,
                          iconToolTipIndex = iconToolTipToIndex[it.iconToolTip]!!.toByte(),
                          firstLine = firstLine,
                          exportedFirstLine = exportedFirstLine,
                          isContainer = it.isContainer,
                          treeId = it.treeId,
                          parentTreeId = it.parentTreeId,
                          canBeHidden = it.canBeHidden
    )
  }

  return ThreadDumpWithAwaitingDependencies(items = items,
                                            icons = icons.map { it.rpcId() },
                                            attributes = attributes.map { it.rpcId() },
                                            stackTraceBodies = stackTraceBodies,
                                            exportedStackTraceBodies = exportedStackTraceBodies,
                                            awaitingDependencies = awaiting,
                                            stateDescriptions = stateDescriptions,
                                            iconToolTips = iconToolTips,
                                            truncatedItemsNumber = truncatedSize)
}

private fun deduplicateTextBodies(
  dumpItems: List<DumpItem>,
  selector: (DumpItem) -> String,
): Pair<List<String>, Map<DumpItem, DumpItemWithTextBodyIndex>> {
  // The first line often carries per-item ids or inline metadata, while the multiline body
  // is commonly repeated across many items, so only the body is shared through the DTO.
  val bodyToItems = dumpItems.map { dumpItem ->
    val separatedText = splitFirstLineAndBody(selector(dumpItem))
    TextWithSeparatedFirstLine(
      firstLine = separatedText.firstLine,
      body = separatedText.body,
      dumpItem = dumpItem,
    )
  }.groupBy { it.body }.toList()

  val bodies = bodyToItems.map { it.first }
  val itemToBodyIndex = bodyToItems.withIndex().flatMap { (index, indexedValue) ->
    indexedValue.second.map {
      it.dumpItem to DumpItemWithTextBodyIndex(it.firstLine, index)
    }
  }.associate { it.first to it.second }
  return bodies to itemToBodyIndex
}

/**
 * Dump item text split into the visible first line and the deduplicated multiline body.
 */
private data class TextWithSeparatedFirstLine(val firstLine: String, val body: String, val dumpItem: DumpItem)

/**
 * Per-item reference to a shared body plus the unique first line for this dump item.
 */
private data class DumpItemWithTextBodyIndex(val firstLine: String, val bodyIndex: Int)
