// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal

import com.intellij.execution.filters.CompositeFilter.ApplyFilterException
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.Filter.ResultItem
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.HyperlinkWithHoverInfo
import com.intellij.execution.filters.HyperlinkWithPopupMenuInfo
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.terminal.actions.TerminalActionUtil
import com.jediterm.terminal.model.hyperlinks.AsyncHyperlinkFilter
import com.jediterm.terminal.model.hyperlinks.LinkInfo
import com.jediterm.terminal.model.hyperlinks.LinkResult
import com.jediterm.terminal.model.hyperlinks.LinkResultItem
import com.jediterm.terminal.ui.TerminalAction
import com.jediterm.terminal.ui.hyperlinks.LinkInfoEx
import com.jediterm.terminal.ui.hyperlinks.LinkInfoEx.HoverConsumer
import com.jediterm.terminal.ui.hyperlinks.LinkInfoEx.PopupMenuGroupProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

internal class JediTermHyperlinkFilterAdapter(
  private val project: Project,
  console: TerminalExecutionConsole?,
  private val widget: JBTerminalWidget,
) : AsyncHyperlinkFilter {

  private val filterWrapper = CompositeFilterWrapper(project, console, widget)

  private val requestChannel: Channel<Request> = Channel(MAX_BUFFERED_REQUESTS, BufferOverflow.DROP_OLDEST)

  init {
    @OptIn(ExperimentalCoroutinesApi::class)
    val coroutineScope = project.service<CoroutineScopeService>().coroutineScope.childScope(
      "JediTerminal Filters",
      // Hyperlinks are an additional feature, so their computation should not consume significant CPU resources.
      // Even though the current implementation guarantees this implicitly, let's limit CPU
      // usage explicitly as a precaution for future changes in the implementation.
      Dispatchers.Default.limitedParallelism(1)
    )
    Disposer.register(widget) {
      coroutineScope.cancel()
      requestChannel.close()
    }
    coroutineScope.launch {
      for (request in requestChannel) {
        processRequest(request.lineInfo, request.future)
      }
    }
  }

  fun addFilter(filter: Filter) {
    filterWrapper.addFilter(filter)
  }

  override fun apply(lineInfo: AsyncHyperlinkFilter.LineInfo): CompletableFuture<LinkResult?> {
    val future = CompletableFuture<LinkResult?>()
    requestChannel.trySend(Request(lineInfo, future))
    return future
  }

  private suspend fun processRequest(lineInfo: AsyncHyperlinkFilter.LineInfo, future: CompletableFuture<LinkResult?>) {
    try {
      val result = runFiltersAndConvert(lineInfo)
      future.complete(result)
    }
    catch (e: Exception) {
      future.completeExceptionally(e)
    }
  }

  private suspend fun runFiltersAndConvert(lineInfo: AsyncHyperlinkFilter.LineInfo): LinkResult? {
    val result = runFilters(lineInfo) ?: return null
    return LinkResult(result.getResultItems().mapNotNull {
      convertResultItem(it)
    })
  }

  private suspend fun runFilters(lineInfo: AsyncHyperlinkFilter.LineInfo): Filter.Result? {
    lineInfo.line ?: return null // do not start read action for outdated lines
    return readAction {
      val line = lineInfo.line ?: return@readAction null
      try {
        filterWrapper.getCompositeFilter().applyFilter(line, line.length)
      }
      catch (applyFilterException: ApplyFilterException) {
        LOG.error(applyFilterException)
        null
      }
    }
  }

  private fun convertResultItem(item: ResultItem): LinkResultItem? {
    val info = item.hyperlinkInfo ?: return null
    return LinkResultItem(item.highlightStartOffset, item.highlightEndOffset, convertInfo(info))
  }

  private fun convertInfo(info: HyperlinkInfo): LinkInfo {
    val builder = LinkInfoEx.Builder().setNavigateCallback(Runnable {
      info.navigate(project)
    })
    if (info is HyperlinkWithPopupMenuInfo) {
      builder.setPopupMenuGroupProvider(object : PopupMenuGroupProvider {
        override fun getPopupMenuGroup(event: MouseEvent): List<TerminalAction> {
          val group = info.getPopupMenuGroup(event)
          val actions = expandGroup(group)
          return actions.map { TerminalActionUtil.createTerminalAction(widget, it) }
        }
      })
    }
    if (info is HyperlinkWithHoverInfo) {
      builder.setHoverConsumer(object : HoverConsumer {
        override fun onMouseEntered(hostComponent: JComponent, linkBounds: Rectangle) {
          info.onMouseEntered(hostComponent, linkBounds)
        }

        override fun onMouseExited() {
          info.onMouseExited()
        }
      })
    }
    return builder.build()
  }

  private fun expandGroup(group: ActionGroup?): Array<AnAction> {
    if (group == null) return AnAction.EMPTY_ARRAY
    if (group is DefaultActionGroup) {
      return group.getChildren(ActionManager.getInstance())
    }
    return group.getChildren(null)
  }

  private class Request(val lineInfo: AsyncHyperlinkFilter.LineInfo, val future: CompletableFuture<LinkResult?>)
}

private val LOG: Logger = logger<JediTermHyperlinkFilterAdapter>()

private const val MAX_BUFFERED_REQUESTS = 10000

@Service(Service.Level.PROJECT)
private class CoroutineScopeService(val coroutineScope: CoroutineScope)
