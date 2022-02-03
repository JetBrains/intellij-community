// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.injected.editor.EditorWindow
import com.intellij.internal.statistic.service.fus.collectors.UIEventLogger
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.impl.DocumentationManager
import com.intellij.lang.documentation.ide.impl.injectedThenHost
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorMouseHoverPopupManager
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.HintText
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.LightweightHint
import com.intellij.ui.ScreenUtil
import com.intellij.ui.ScreenUtil.isMovementTowards
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import kotlin.math.max
import kotlin.math.min

internal class InitCtrlMouseHandlerActivity : StartupActivity {

  override fun runActivity(project: Project) {
    if (!Registry.`is`("documentation.v2.ctrl.mouse")) {
      return
    }
    project.service<CtrlMouseHandler2>()
  }
}

@VisibleForTesting
@Service
class CtrlMouseHandler2(
  private val project: Project,
) : EditorMouseMotionListener,
    EditorMouseListener,
    FileEditorManagerListener,
    VisibleAreaListener,
    KeyAdapter(),
    Disposable {

  init {
    val eventMulticaster = EditorFactory.getInstance().eventMulticaster
    eventMulticaster.addEditorMouseMotionListener(this, this)
    eventMulticaster.addEditorMouseListener(this, this)
    project.messageBus.connect(this).subscribe(FILE_EDITOR_MANAGER, this)
  }

  private var myLastMouseLocation: Point? = null

  override fun mouseMoved(e: EditorMouseEvent) {
    if (e.isConsumed) {
      return
    }
    val editor = e.editor as? EditorEx
                 ?: return
    if (editor.project != project) {
      // EditorEventMulticaster sends events from all projects, we only need events in this project
      return
    }
    val mouseEvent: MouseEvent = e.mouseEvent
    if (ignoreMovement(mouseEvent.locationOnScreen)) {
      return
    }
    if (e.area != EditorMouseEventArea.EDITING_AREA || !e.isOverText) {
      cancelAndClear()
      return
    }
    val action = getCtrlMouseAction(mouseEvent.modifiersEx)
    if (action == null) {
      cancelAndClear()
      return
    }
    cancelHandlerJob()
    handle(CtrlMouseRequest(editor, e.offset, action))
  }

  private fun ignoreMovement(screenPoint: Point): Boolean {
    val previousMouseLocation = myLastMouseLocation
    myLastMouseLocation = screenPoint
    val hint = myState?.hint
               ?: return false
    val hintComponent = hint.component
    if (!hintComponent.isShowing) {
      return false
    }
    val hintBounds = Rectangle(hintComponent.locationOnScreen, hintComponent.size)
    return isMovementTowards(previousMouseLocation, screenPoint, hintBounds)
  }

  override fun keyPressed(e: KeyEvent) {
    cancelHandlerJob()
    val state = checkNotNull(myState) {
      "state must be non-null when the key listener is running"
    }
    val action = getCtrlMouseAction(e.modifiersEx)
    if (action == null) {
      clearState()
      return
    }
    val request = state.request
    if (action === request.action) {
      return
    }
    handle(request.copy(action = action))
  }

  override fun keyReleased(e: KeyEvent) {
    keyPressed(e)
  }

  override fun mouseReleased(e: EditorMouseEvent) {
    cancelAndClear()
  }

  override fun selectionChanged(event: FileEditorManagerEvent) {
    cancelAndClear()
  }

  override fun visibleAreaChanged(e: VisibleAreaEvent) {
    cancelAndClear()
  }

  private val cs = CoroutineScope(SupervisorJob())

  private var myState: CtrlMouseState? = null
    get() {
      EDT.assertIsEdt()
      return field
    }
    set(value) {
      EDT.assertIsEdt()
      field = value
    }

  @TestOnly
  fun handlerJob(): Job {
    return cs.coroutineContext.job.children.single()
  }

  override fun dispose() {
    cs.cancel("CtrlMouseHandler disposal")
    clearState()
  }

  private fun cancelAndClear() {
    cancelHandlerJob()
    clearState()
  }

  private fun cancelHandlerJob() {
    cs.coroutineContext.job.cancelChildren()
  }

  private fun clearState() {
    val state = myState
    if (state != null) {
      myState = null
      Disposer.dispose(state)
    }
  }

  private fun handle(request: CtrlMouseRequest) {
    cs.launch(Dispatchers.EDT, start = CoroutineStart.UNDISPATCHED) {
      val result = compute(request)
      if (result != null) {
        highlightAndHint(request, result)
      }
      else {
        clearState()
      }
    }
  }

  private suspend fun compute(request: CtrlMouseRequest): CtrlMouseResult? = withContext(Dispatchers.IO) {
    try {
      constrainedReadAction(ReadConstraint.withDocumentsCommitted(project)) {
        computeInReadAction(request)
      }
    }
    catch (e: IndexNotReadyException) {
      DumbService.getInstance(project).showDumbModeNotification(
        CodeInsightBundle.message("notification.element.information.is.not.available.during.index.update")
      )
      null
    }
  }

  private fun computeInReadAction(request: CtrlMouseRequest): CtrlMouseResult? {
    return injectedThenHost(project, request.editor, request.offset) { editor, file, offset ->
      val data: CtrlMouseData? = request.action.getCtrlMouseData(editor, file, offset)
      if (data == null) {
        return@injectedThenHost null
      }
      val result = CtrlMouseResult(
        data.isNavigatable,
        data.ranges,
        data.hintText,
        data.target?.createPointer(),
        data.target?.javaClass,
      )
      if (editor is EditorWindow) {
        val manager = InjectedLanguageManager.getInstance(project)
        val hostRanges = result.ranges.map {
          manager.injectedToHost(editor.injectedFile, it)
        }
        result.copy(ranges = hostRanges)
      }
      else {
        result
      }
    }
  }

  private fun highlightAndHint(request: CtrlMouseRequest, result: CtrlMouseResult) {
    val state = myState
    if (state != null) {
      if (state.request.action === request.action &&
          state.ranges == result.ranges) {
        // highlighter already set
        return
      }
      clearState()
    }
    if (!result.isNavigatable && result.hintText == null) {
      return
    }
    val (editor, offset, _) = request
    if (!checkRanges(result, editor.document)) {
      return
    }
    val hint = showHint(editor, offset, result)
    editor.scrollingModel.addVisibleAreaListener(this)
    editor.contentComponent.addKeyListener(this)
    if (result.isNavigatable) {
      editor.setCustomCursor(CtrlMouseHandler::class.java, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
    }
    val attributes = textAttributes(result.isNavigatable)
    val highlighters = result.ranges.map { range ->
      editor.markupModel.addRangeHighlighter(
        range.startOffset, range.endOffset, HighlighterLayer.HYPERLINK,
        NavigationUtil.patchAttributesColor(attributes, range, editor),
        HighlighterTargetArea.EXACT_RANGE
      )
    }
    myState = CtrlMouseState(request, result.ranges, hint).also {
      Disposer.register(it) {
        for (highlighter in highlighters) {
          highlighter.dispose()
        }
        editor.setCustomCursor(CtrlMouseHandler::class.java, null)
        editor.contentComponent.removeKeyListener(this)
        editor.scrollingModel.removeVisibleAreaListener(this)
      }
    }
  }

  private fun showHint(editor: EditorEx, hostOffset: Int, result: CtrlMouseResult): LightweightHint? {
    val skipHint = EditorMouseHoverPopupManager.getInstance().isHintShown ||
                   DocumentationManager.instance(project).isPopupVisible ||
                   ApplicationManager.getApplication().isUnitTestMode
    if (skipHint) {
      return null
    }
    val text = result.hintText
               ?: return null
    UIEventLogger.QuickNavigateInfoPopupShown2.log(project, result.targetClass!!)
    val hyperlinkListener = result.targetPointer?.let {
      HintHyperlinkListener(editor, it)
    }
    val component = HintUtil.createInformationLabel(text, hyperlinkListener, null, null).also {
      it.border = JBUI.Borders.empty(6, 6, 5, 6)
    }
    return showHint(editor, hostOffset, component)
  }

  private inner class HintHyperlinkListener(
    private val editor: Editor,
    private val targetPointer: Pointer<out DocumentationTarget>,
  ) : HyperlinkListener {

    override fun hyperlinkUpdate(e: HyperlinkEvent) {
      if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) {
        return
      }
      val description = e.description
      if (!description.startsWith(PSI_ELEMENT_PROTOCOL)) {
        return
      }
      cs.launch(Dispatchers.EDT + ModalityState.current().asContextElement(), start = CoroutineStart.UNDISPATCHED) {
        val ok = DocumentationManager.instance(project).activateInlineLinkS(
          targetPointer::dereference, description, editor, editorPoint(e, editor)
        )
        if (ok) {
          clearState()
        }
      }
    }
  }
}

private class CtrlMouseState(
  val request: CtrlMouseRequest,
  val ranges: List<TextRange>,
  var hint: LightweightHint?,
) : Disposable {

  init {
    hint?.addHintListener {
      hint = null
    }
  }

  override fun dispose() {
    hint?.hide()
  }
}

private data class CtrlMouseRequest(
  val editor: EditorEx,
  val offset: Int,
  val action: CtrlMouseAction,
)

private data class CtrlMouseResult(
  val isNavigatable: Boolean,
  val ranges: List<TextRange>,
  val hintText: @HintText String?,
  val targetPointer: Pointer<out DocumentationTarget>?,
  val targetClass: Class<out Any>?, // for stats
)

private fun getCtrlMouseAction(modifiers: Int): CtrlMouseAction? {
  if (modifiers == 0) {
    return null
  }
  val keymapManager = KeymapManager.getInstance()
                      ?: return null
  val shortcut = MouseShortcut(MouseEvent.BUTTON1, modifiers, 1)
  val actionIds = keymapManager.activeKeymap.getActionIds(shortcut)
  return actionIds.mapNotNull(::getCtrlMouseAction).singleOrNull()
}

private fun getCtrlMouseAction(actionId: String): CtrlMouseAction? {
  return ActionManager.getInstance().getAction(actionId) as? CtrlMouseAction
}

private fun checkRanges(result: CtrlMouseResult, document: Document): Boolean {
  val docRange = TextRange(0, document.textLength)
  return result.ranges.all { range ->
    range in docRange
  }
}

private fun showHint(hostEditor: Editor, hostOffset: Int, component: JComponent): LightweightHint {
  val hint = LightweightHint(wrapInScrollPaneIfNeeded(component, hostEditor))
  val position = hostEditor.offsetToLogicalPosition(hostOffset)
  var constraint = HintManager.ABOVE
  var p = HintManagerImpl.getHintPosition(hint, hostEditor, position, constraint)
  if (p.y - hint.component.preferredSize.height < 0) {
    constraint = HintManager.UNDER
    p = HintManagerImpl.getHintPosition(hint, hostEditor, position, constraint)
  }
  val hintHint = HintManagerImpl.createHintHint(hostEditor, p, hint, constraint).setContentActive(false)
  HintManagerImpl.getInstanceImpl().showEditorHint(
    hint, hostEditor, p,
    HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
    0, false, hintHint
  )
  return hint
}

private fun wrapInScrollPaneIfNeeded(component: JComponent, editor: Editor): JComponent {
  val rectangle = ScreenUtil.getScreenRectangle(editor.contentComponent)
  val maxWidth = (0.9 * max(640, rectangle.width)).toInt()
  val maxHeight = (0.33 * max(480, rectangle.height)).toInt()
  val preferredSize = component.preferredSize
  if (preferredSize.width <= maxWidth && preferredSize.height <= maxHeight) {
    return component
  }
  // We expect documentation providers to exercise good judgement in limiting the displayed information,
  // but in any case, we don't want the hint to cover the whole screen, so we also implement certain limiting here.
  return ScrollPaneFactory.createScrollPane(component, true).also {
    it.preferredSize = Dimension(
      min(preferredSize.width, maxWidth),
      min(preferredSize.height, maxHeight),
    )
  }
}

private fun textAttributes(navigatable: Boolean): TextAttributes? {
  return if (navigatable) {
    EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
  }
  else {
    TextAttributes(null, HintUtil.getInformationColor(), null, null, Font.PLAIN)
  }
}

private fun editorPoint(event: HyperlinkEvent, editor: Editor): Point {
  val inputEvent = event.inputEvent as MouseEvent // link could be activated only with a mouse
  return Point(inputEvent.locationOnScreen).also {
    SwingUtilities.convertPointFromScreen(it, editor.contentComponent)
  }
}
