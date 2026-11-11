// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.console

import com.intellij.build.BuildTextConsoleView
import com.intellij.build.ExecutionNode
import com.intellij.build.console.BuildConsoleViewImpl.Companion.getConsoleHtml
import com.intellij.build.console.BuildConsoleViewImpl.Companion.getHyperlinkHandler
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.Failure
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputBuildEvent
import com.intellij.build.events.OutputId
import com.intellij.build.events.OutputReferenceEvent
import com.intellij.codeWithMe.ClientId
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewWithDelegate
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.ObservableConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.ClientEditorManager.Companion.getClientEditor
import com.intellij.openapi.editor.ComponentInlayAlignment
import com.intellij.openapi.editor.ComponentInlayRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.addComponentInlay
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.Alarm
import com.intellij.util.text.nullize
import org.jetbrains.annotations.CheckReturnValue
import org.jetbrains.annotations.TestOnly
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

internal class BuildConsoleViewImplV2(
  private val project: Project,
  override val delegate: ConsoleViewImpl,
  private val node: ExecutionNode? = null,
) : BuildConsoleView, ConsoleViewWithDelegate, ExecutionConsole by delegate {

  private val state = ConsoleViewState(delegate)

  /**
   * `@TestOnly` per-node projection of the text this single console renders. The output text is read directly from the
   * production console document (which is why ANSI decoding and carriage-return handling are exactly what production
   * produced): in single-console mode a node's output is dispatched to that node's own console, so this console's whole
   * document is the projection of [node]. Message/issue/failure inlays are Swing components and therefore absent from
   * the document, so the inlay infos are recorded here keyed by the node id and rendered back to plain text on query.
   * Recorded only in unit-test mode so production stays bare.
   */
  private val recordNodeText: Boolean = ApplicationManager.getApplication().isUnitTestMode
  private val consoleInlayInfos = ConcurrentHashMap<Any, MutableList<ConsoleInlayInfo>>()

  /** Count of Swing component inlays added. Extra messages are printed as plain text (IDEA-374341). */
  @Volatile private var inlayCount = 0

  /** User intent to follow the tail. Only a user scroll gesture changes it, not content growth. Read and written on EDT. */
  private var autoScroll = true
  private var followListenersAttached = false

  /** Restores the component that the last click flashed. Read and written on EDT. */
  private val flashAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
  private var flashRestore: (() -> Unit)? = null


  init {
    delegate.editor?.configureEditor()
    Disposer.register(this, delegate)
  }

  override fun onEvent(event: BuildEvent) {
    // Output ends up in the console document (read back in getNodeOutputText); message/issue/failure text is recorded
    // in addConsoleInlay as the inlay info the corresponding Swing inlay shows (inlays are not part of the document).
    when (event) {
      is MessageEvent -> onMessageEvent(event)
      is OutputBuildEvent -> onOutputEvent(event)
      is OutputReferenceEvent -> onOutputReferenceEvent(event)
      else -> onBuildEvent(event)
    }
  }

  private fun onMessageEvent(event: MessageEvent) {
    val html = event.getConsoleHtml()
    if (html == null) {
      state.addNodeOutputMarkers(event.id, event.outputIds)
      return
    }
    addConsoleInlay(event.id, ConsoleInlayInfo()
      .withText(html)
      .withKind(event.result.kind.consoleInlayKind)
      .withOutputIds(event.outputIds)
      .withHyperlinkListener {
        if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
          val hyperlinkHandler = event.getHyperlinkHandler(project, delegate)
          hyperlinkHandler(it.description)
        }
      }
    )
  }

  private fun onOutputEvent(event: OutputBuildEvent) {
    BuildTextConsoleView.print(delegate, event.message, event.outputType, event.id)
  }

  private fun onOutputReferenceEvent(event: OutputReferenceEvent) {
    state.addNodeOutputMarkers(event.parentId, event.outputIds)
  }

  @TestOnly
  override fun getNodeOutputText(nodeId: Any): String {
    if (nodeId == node?.id) {
      return delegate.text
    }

    val result = StringBuilder()

    state.accessFlushed {
      for (outputRange in sortedOutputMarkers(nodeId)) {
        result.append(editor.document.getText(outputRange))
      }
    }

    for (inlayInfo in consoleInlayInfos[nodeId].orEmpty()) {
      result.append(inlayInfo.plainText)
    }
    return result.toString()
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
    state.accessFlushed {
      val inlay = nodeInlays(nodeId).firstOrNull()
      val bounds = inlay?.bounds
      if (inlay != null && bounds != null) {
        editor.caretModel.moveToOffset(inlay.offset)
        if (ClientId.isCurrentlyUnderLocalId) {
          editor.scrollingModel.scrollVertically(bounds.y)
        } else {
          val targetOffset = editor.offsetToLogicalPosition(inlay.offset)
          editor.scrollingModel.scrollTo(targetOffset, ScrollType.CENTER)
        }
        editor.flashInlayComponent(inlay.renderer.component)
      }
      else {
        val outputRanges = sortedOutputMarkers(nodeId)
        val outputRange = outputRanges.firstOrNull()
        if (outputRange != null) {
          editor.caretModel.moveToOffset(outputRange.startOffset)
          editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
        }
        editor.selectOutputText(outputRanges)
      }
    }
  }

  /** Flashes the inlay component background, so the user finds the clicked callout among similar ones. */
  private fun Editor.flashInlayComponent(component: JComponent) {
    val flashColor = colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR) ?: return
    flashAlarm.cancelAllRequests()
    flashRestore?.invoke()
    val originalOpaque = component.isOpaque
    val originalBackground = component.background
    component.isOpaque = true
    component.background = flashColor
    component.repaint()
    val restore = {
      component.isOpaque = originalOpaque
      component.background = originalBackground
      component.repaint()
    }
    flashRestore = restore
    flashAlarm.addRequest({
      restore()
      flashRestore = null
    }, 700)
  }

  private fun Editor.selectOutputText(outputRanges: List<TextRange>) {
    caretModel.removeSecondaryCarets()
    caretModel.primaryCaret.removeSelection()
    for ([index, outputRange] in outputRanges.withIndex()) {
      if (index == 0) {
        caretModel.primaryCaret.moveToOffset(outputRange.startOffset)
        caretModel.primaryCaret.setSelection(outputRange.startOffset, outputRange.endOffset)
      }
      else {
        val visualPosition = offsetToVisualPosition(outputRange.startOffset)
        val secondaryCaret = caretModel.addCaret(visualPosition, false) ?: run {
          thisLogger().warn("Failed to allocate console caret at $visualPosition")
          continue
        }
        secondaryCaret.setSelection(outputRange.startOffset, outputRange.endOffset)
      }
    }
  }

  private fun Editor.configureEditor() {
    settings.isHighlightSelectionOccurrences = false
  }

  private fun isVScrollAtBottom(editor: EditorEx): Boolean {
    val bar = editor.scrollPane.verticalScrollBar
    return bar.value >= bar.maximum - bar.visibleAmount
  }

  private fun ensureFollowListeners(editor: EditorEx) {
    if (followListenersAttached) return
    followListenersAttached = true
    val scrollPane = editor.scrollPane
    // Only a user gesture changes the follow intent. Do not react to raw scrollbar value changes, because content growth
    // also moves the bar and would switch following off.
    scrollPane.addMouseWheelListener { e ->
      if (!e.isShiftDown) SwingUtilities.invokeLater { autoScroll = isVScrollAtBottom(editor) }
    }
    val mouse = object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) { autoScroll = isVScrollAtBottom(editor) }
      override fun mouseDragged(e: MouseEvent) { autoScroll = isVScrollAtBottom(editor) }
    }
    val bar = scrollPane.verticalScrollBar
    bar.addMouseListener(mouse)
    bar.addMouseMotionListener(mouse)
  }

  private fun addConsoleInlay(nodeId: Any, inlayInfo: ConsoleInlayInfo) {
    if (recordNodeText) {
      consoleInlayInfos.computeIfAbsent(nodeId) { CopyOnWriteArrayList() }.add(inlayInfo)
    }
    // Beyond the registry limit render the message as plain text. Thousands of Swing inlays make the editor layout
    // O(n) on every document change and lag the whole UI (IDEA-374341). Print off the EDT and let the console batch it;
    // a per-message state.access forces an EDT round-trip and a flush, which is the slow part.
    if (inlayCount >= Registry.intValue("build.console.max.inlays", 100)) {
      val contentType = if (inlayInfo.kind == BuildConsoleViewInlay.Kind.ERROR) ConsoleViewContentType.ERROR_OUTPUT
      else ConsoleViewContentType.NORMAL_OUTPUT
      delegate.print(inlayInfo.plainText, contentType)
      return
    }
    state.addNodeOutputMarkers(nodeId, inlayInfo.outputIds)
    state.access {
      inlayCount++
      (editor as? EditorEx)?.let { ensureFollowListeners(it) }
      val inlayProperties = InlayProperties()
        .showAbove(true)
      val inlayComponent = when (inlayInfo.outputIds.isEmpty()) {
        true -> BuildConsoleViewInlay.panel(editor.colorsScheme, inlayInfo.text, inlayInfo.kind, inlayInfo.hyperlinkListener)
        else -> BuildConsoleViewInlay.comment(editor.colorsScheme, inlayInfo.text, inlayInfo.hyperlinkListener)
      }
      val inlayOffset = sortedOutputMarkers(nodeId).firstOrNull()?.startOffset ?: editor.document.textLength
      val inlay = editor.addComponentInlay(
        inlayOffset,
        inlayProperties,
        inlayComponent,
        ComponentInlayAlignment.FIT_VIEWPORT_WIDTH
      )
      if (inlay != null) {
        addNodeInlay(nodeId, inlay)
      }
      // An inlay adds height outside the document, so the native console auto-follow does not fire, and the inlay gets
      // its height only after its component is laid out. Follow the tail on that first layout, honoring the user intent.
      // requestScrollingToEnd flushes any pending text first, so text that follows an inlay is included.
      inlayComponent.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent) {
          inlayComponent.removeComponentListener(this)
          if (autoScroll) delegate.requestScrollingToEnd()
        }
      })
    }
  }

  @CheckReturnValue
  private data class ConsoleInlayInfo(
    val text: String = "",
    /** Plain text projection of [text], shown to `@TestOnly` [getNodeOutputText]. */
    val plainText: String = "",
    val kind: BuildConsoleViewInlay.Kind = BuildConsoleViewInlay.Kind.ERROR,
    val outputIds: Collection<Any> = emptyList(),
    val hyperlinkListener: HyperlinkListener? = null,
  ) {

    fun withText(text: String): ConsoleInlayInfo =
      copy(text = plainTextToHtml(text.trim()), plainText = htmlToPlainText(text))

    fun withKind(kind: BuildConsoleViewInlay.Kind): ConsoleInlayInfo =
      copy(kind = kind)

    fun withOutputIds(outputIds: Collection<Any>): ConsoleInlayInfo =
      copy(outputIds = outputIds)

    fun withHyperlinkListener(hyperlinkListener: HyperlinkListener): ConsoleInlayInfo =
      copy(hyperlinkListener = hyperlinkListener)
  }

  private class ConsoleViewState(private val consoleView: ConsoleViewImpl) {

    private val outputMarkers = HashMap<Any, RangeMarker>()
    private val nodeToOutputMarkers = ConcurrentHashMap<Any, MutableSet<OutputId>>()
    private val nodeToInlays = HashMap<Any, MutableList<Inlay<ComponentInlayRenderer<JComponent>>>>()

    private val pendingStateActions = ConcurrentLinkedQueue<ConsoleViewStateAccessor.() -> Unit>()
    private val isDrainScheduled = AtomicBoolean()

    init {
      consoleView.addChangeListener(object : ObservableConsoleView.ChangeListener {
        override fun textRangeAdded(textId: Any, range: TextRange) {
          val document = consoleView.editor?.document ?: return
          outputMarkers[textId] = document.createRangeMarker(range)
        }
      }, consoleView)
    }

    fun addNodeOutputMarkers(nodeId: Any, outputIds: Collection<Any>) {
      nodeToOutputMarkers.computeIfAbsent(nodeId) { ConcurrentHashMap.newKeySet() }
        .addAll(outputIds)
    }

    fun access(action: ConsoleViewStateAccessor.() -> Unit) {
      pendingStateActions.add(action)
      if (isDrainScheduled.compareAndSet(false, true)) {
        ApplicationManager.getApplication().invokeLater {
          consoleView.performWhenNoDeferredOutput {
            isDrainScheduled.set(false)
            runPendingActions()
          }
        }
      }
    }

    fun accessFlushed(action: ConsoleViewStateAccessor.() -> Unit) {
      invokeAndWaitIfNeeded {
        WriteIntentReadAction.run {
          consoleView.flushDeferredText()
          pendingStateActions.add(action)
          runPendingActions()
        }
      }
    }

    private fun runPendingActions() {
      val editor = consoleView.editor
      if (editor == null) {
        pendingStateActions.clear()
        return
      }
      val targetEditor = getClientEditor(editor, ClientId.currentOrNull)
      while (true) {
        val action = pendingStateActions.poll() ?: break
        action(ConsoleViewStateAccessor(targetEditor))
      }
    }

    inner class ConsoleViewStateAccessor(val editor: Editor) {

      fun addNodeInlay(nodeId: Any, inlay: Inlay<ComponentInlayRenderer<JComponent>>) {
        nodeToInlays.computeIfAbsent(nodeId) { ArrayList() }.add(inlay)
      }

      // ponytail: the invalid inlays stay in the map and are filtered on read, like the markers above.
      fun nodeInlays(nodeId: Any): Sequence<Inlay<ComponentInlayRenderer<JComponent>>> {
        return nodeToInlays[nodeId].orEmpty().asSequence()
          .filter { it.isValid }
      }

      fun sortedOutputMarkers(nodeId: Any): List<TextRange> {
        val markers = nodeToOutputMarkers[nodeId] ?: return emptyList()

        val ranges = markers.asSequence()
          .mapNotNull { outputMarkers[it] }
          .filter { it.isValid }
          .map { it.textRange }
          .filter { !it.isEmpty }
          .sortedBy { it.startOffset }
          .toList()

        val result = ArrayList<TextRange>(ranges.size)
        for (range in ranges) {
          val last = result.lastOrNull()
          if (last != null && range.startOffset <= last.endOffset) {
            result[result.lastIndex] = last.union(range)
          }
          else {
            result.add(range)
          }
        }
        return result
      }
    }
  }

  companion object {

    private val TAG_PATTERN = Regex("<[^>]*>")
    private val A_PATTERN = Regex("<a ([^>]* )?href=[\"']([^>]*)[\"'][^>]*>")
    private const val A_CLOSING = "</a>"
    private val NEW_LINES = setOf("<br>", "</br>", "<br/>", "<p>", "</p>", "<p/>", "<pre>", "</pre>")

    /**
     * Converts the HTML produced for message/issue/failure descriptions into the plain text the old per-node console
     * printed: `<a>` tags are replaced with their link text, line-break tags become `\n`, other tags are kept
     * verbatim, and a trailing `\n` is appended (matching the old `printHtml`).
     */
    private fun htmlToPlainText(text: String): String {
      val result = StringBuilder()
      var content = StringUtil.convertLineSeparators(text)
      while (true) {
        val tagMatch = TAG_PATTERN.find(content)
        if (tagMatch == null) {
          result.append(content)
          break
        }
        result.append(content, 0, tagMatch.range.first)
        val tag = tagMatch.value
        if (A_PATTERN.matches(tag)) {
          val linkEnd = content.indexOf(A_CLOSING, tagMatch.range.last + 1)
          if (linkEnd > 0) {
            result.append(content.substring(tagMatch.range.last + 1, linkEnd).replace(TAG_PATTERN, ""))
            content = content.substring(linkEnd + A_CLOSING.length)
            continue
          }
        }
        if (tag in NEW_LINES) {
          result.append('\n')
        }
        else {
          result.append(tag)
        }
        content = content.substring(tagMatch.range.last + 1)
      }
      result.append('\n')
      return result.toString()
    }

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