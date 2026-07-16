// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.BuildConsoleUtils
import com.intellij.build.BuildTextConsoleView
import com.intellij.build.console.BuildConsoleViewImpl.Companion.getHyperlinkInfo
import com.intellij.build.console.BuildConsoleViewImpl.Companion.getHyperlinkText
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.Failure
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.OutputReferenceEvent
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.text.nullize
import kotlinx.html.HTML
import kotlinx.html.body
import kotlinx.html.html
import kotlinx.html.link
import kotlinx.html.stream.createHTML
import org.jetbrains.annotations.CheckReturnValue
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

internal class BuildConsoleViewImplV2(
  private val project: Project,
  override val delegate: ConsoleViewImpl,
) : BuildConsoleView, ConsoleViewWithDelegate, ExecutionConsole by delegate {

  private val state = ConsoleViewState(delegate)

  init {
    Disposer.register(this, delegate)
  }

  override fun onEvent(event: BuildEvent) {
    when (event) {
      is BuildIssueEvent -> onBuildIssueEvent(event)
      is FileMessageEvent -> onFileMessageEvent(event)
      is MessageEvent -> onMessageEvent(event)
      is OutputBuildEvent -> onOutputEvent(event)
      is OutputReferenceEvent -> onOutputReferenceEvent(event)
      else -> onBuildEvent(event)
    }
  }

  private fun onBuildIssueEvent(event: BuildIssueEvent) {
    val quickFixes = event.issue.quickFixes.associateBy { it.id }
    addConsoleInlay(event.id, ConsoleInlayInfo()
      .withText(event.issue.description)
      .withKind(event.result.kind.consoleInlayKind)
      .withOutputIds(event.outputIds)
      .withHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          val dataContext = BuildConsoleUtils.getDataContext(delegate)
          quickFixes[it.description]?.runQuickFix(project, dataContext)
        }
      }
    )
  }

  private fun onFileMessageEvent(event: FileMessageEvent) {
    val hyperlinkId = "file-info"
    val hyperlinkText = getHyperlinkText(event.filePosition)
    val hyperlinkInfo = getHyperlinkInfo(project, event.filePosition)
    if (hyperlinkText == null || hyperlinkInfo == null) {
      onMessageEvent(event)
      return
    }
    addConsoleInlay(event.id, ConsoleInlayInfo()
      .withKind(event.result.kind.consoleInlayKind)
      .withOutputIds(event.outputIds)
      .withHtmlText {
        body {
          link(hyperlinkId, hyperlinkText)
          text(": " + event.message)
        }
      }.withHyperlinkListener { event ->
        if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          if (event.description == hyperlinkId) {
            hyperlinkInfo.navigate(project)
          }
        }
      }
    )
  }

  private fun onMessageEvent(event: MessageEvent) {
    val text = event.description.nullize() ?: event.result.details.nullize()
    if (text == null) {
      state.access {
        addNodeOutputMarkers(event.id, event.outputIds)
      }
      return
    }
    addConsoleInlay(event.id, ConsoleInlayInfo()
      .withText(text)
      .withKind(event.result.kind.consoleInlayKind)
      .withOutputIds(event.outputIds)
    )
  }

  private fun onOutputEvent(event: OutputBuildEvent) {
    val startOffset = delegate.contentSize
    BuildTextConsoleView.print(delegate, event.message, event.outputType)
    val endOffset = delegate.contentSize
    state.access {
      // The start and end offsets are invalid when the console size was reduced after printing.
      // For example, when single `\r` is printed or console buffer was truncated due to an overflow.
      if (endOffset in startOffset..editor.document.textLength) {
        addOutputMarker(event.id, startOffset, endOffset)
      }
    }
  }

  private fun onOutputReferenceEvent(event: OutputReferenceEvent) {
    state.access {
      addNodeOutputMarkers(event.startId, event.outputIds)
    }
  }

  private fun onBuildEvent(event: BuildEvent) {
    addConsoleInlay(event.id, ConsoleInlayInfo()
      .withText(event.description ?: event.message)
    )
  }

  override fun onFailure(nodeId: Any, failure: Failure) {
    val text = (failure.description ?: failure.message ?: failure.error?.message).nullize() ?: return
    addConsoleInlay(nodeId, ConsoleInlayInfo()
      .withText(text)
      .withHyperlinkListener { event ->
        val notification = failure.notification ?: return@withHyperlinkListener
        notification.listener?.hyperlinkUpdate(notification, event)
      }
    )
  }

  override fun scrollToNodeOutput(nodeId: Any) {
    state.access {
      val outputMarker = sortedOutputMarkers(nodeId).firstOrNull()
      if (outputMarker != null) {
        editor.caretModel.moveToOffset(outputMarker.startOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
      }
    }
  }

  override fun selectProgressOutput(nodeId: Any) {
    state.access {
      editor.caretModel.removeSecondaryCarets()
      editor.caretModel.primaryCaret.removeSelection()
      for ((index, outputMarker) in sortedOutputMarkers(nodeId).withIndex()) {
        if (index == 0) {
          editor.caretModel.primaryCaret.moveToOffset(outputMarker.startOffset)
          editor.caretModel.primaryCaret.setSelection(outputMarker.startOffset, outputMarker.endOffset)
        }
        else {
          val visualPosition = editor.offsetToVisualPosition(outputMarker.startOffset)
          val secondaryCaret = editor.caretModel.addCaret(visualPosition, false) ?: run {
            thisLogger().error("Failed to allocate console caret at $visualPosition")
            continue
          }
          secondaryCaret.setSelection(outputMarker.startOffset, outputMarker.endOffset)
        }
      }
    }
  }

  private fun addConsoleInlay(nodeId: Any, inlayInfo: ConsoleInlayInfo) {
    val consoleOffset = delegate.contentSize
    state.access {
      when (inlayInfo.outputIds.isEmpty()) {
        true -> when (consoleOffset <= editor.document.textLength) {
          true -> addNodeOutputMarker(nodeId, consoleOffset)
          else -> addNodeOutputMarker(nodeId, editor.document.textLength)
        }
        else -> addNodeOutputMarkers(nodeId, inlayInfo.outputIds)
      }
      val inlayProperties = InlayProperties()
        .showAbove(true)
      val inlayComponent = when (inlayInfo.outputIds.isEmpty()) {
        true -> BuildConsoleViewInlay.panel(editor.colorsScheme, inlayInfo.text, inlayInfo.kind, inlayInfo.hyperlinkListener)
        else -> BuildConsoleViewInlay.comment(editor.colorsScheme, inlayInfo.text, inlayInfo.hyperlinkListener)
      }
      val inlayOffset = sortedOutputMarkers(nodeId).firstOrNull()?.startOffset ?: return@access
      editor.addComponentInlay(inlayOffset, inlayProperties, inlayComponent, ComponentInlayAlignment.FIT_VIEWPORT_WIDTH)
    }
  }

  @CheckReturnValue
  private data class ConsoleInlayInfo(
    val text: String = "",
    val kind: BuildConsoleViewInlay.Kind = BuildConsoleViewInlay.Kind.ERROR,
    val outputIds: Collection<Any> = emptyList(),
    val hyperlinkListener: HyperlinkListener? = null,
  ) {

    fun withText(text: String): ConsoleInlayInfo =
      copy(text = plainTextToHtml(text.trim()))

    fun withHtmlText(block: HTML.() -> Unit = {}): ConsoleInlayInfo =
      copy(text = createHTML().html(null, block).trim())

    fun withKind(kind: BuildConsoleViewInlay.Kind): ConsoleInlayInfo =
      copy(kind = kind)

    fun withOutputIds(outputIds: Collection<Any>): ConsoleInlayInfo =
      copy(outputIds = outputIds)

    fun withHyperlinkListener(hyperlinkListener: HyperlinkListener): ConsoleInlayInfo =
      copy(hyperlinkListener = hyperlinkListener)
  }

  private class ConsoleViewState(private val consoleView: ConsoleViewImpl) {

    private val outputMarkers = HashMap<Any, RangeMarker>()
    private val nodeToOutputMarkers = HashMap<Any, MutableSet<RangeMarker>>()

    fun access(action: ConsoleViewStateAccessor.() -> Unit) {
      invokeAndWaitIfNeeded {
        consoleView.performWhenNoDeferredOutput {
          val editor = consoleView.editor ?: return@performWhenNoDeferredOutput
          val accessor = ConsoleViewStateAccessor(editor)
          accessor.action()
        }
      }
    }

    inner class ConsoleViewStateAccessor(val editor: Editor) {

      fun addOutputMarker(outputId: Any, startOffset: Int, endOffset: Int) {
        outputMarkers[outputId] = editor.document.createRangeMarker(startOffset, endOffset)
      }

      fun addNodeOutputMarker(nodeId: Any, offset: Int) {
        nodeToOutputMarkers.computeIfAbsent(nodeId) { HashSet() }
          .add(editor.document.createRangeMarker(offset, offset))
      }

      fun addNodeOutputMarkers(nodeId: Any, outputIds: Collection<Any>) {
        nodeToOutputMarkers.computeIfAbsent(nodeId) { HashSet() }
          .addAll(outputIds.mapNotNull { outputMarkers[it] })
      }

      fun sortedOutputMarkers(nodeId: Any): Sequence<RangeMarker> {
        return nodeToOutputMarkers[nodeId].orEmpty().asSequence()
          .filter { it.isValid }
          .sortedBy { it.startOffset }
      }
    }
  }

  companion object {

    private val MessageEvent.Kind.consoleInlayKind: BuildConsoleViewInlay.Kind
      get() = when (this) {
        MessageEvent.Kind.ERROR -> BuildConsoleViewInlay.Kind.ERROR
        MessageEvent.Kind.WARNING -> BuildConsoleViewInlay.Kind.WARNING
        MessageEvent.Kind.INFO -> BuildConsoleViewInlay.Kind.INFO
        MessageEvent.Kind.SIMPLE -> BuildConsoleViewInlay.Kind.INFO
        MessageEvent.Kind.STATISTICS -> BuildConsoleViewInlay.Kind.INFO
      }

    private fun plainTextToHtml(text: String): String {
      return text.splitToSequence("\r\n", "\n", "\r")
        .joinToString("<br/>\n") {
          val trimmed = it.trimStart(' ')
          "&nbsp;".repeat(it.length - trimmed.length) + trimmed
        }
    }
  }
}