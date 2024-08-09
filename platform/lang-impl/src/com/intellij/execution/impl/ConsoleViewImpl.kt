// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.execution.impl

import com.google.common.base.CharMatcher
import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.codeInsight.navigation.IncrementalSearchHandler
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase
import com.intellij.codeWithMe.ClientId.Companion.currentOrNull
import com.intellij.codeWithMe.ClientId.Companion.isCurrentlyUnderLocalId
import com.intellij.execution.ConsoleFolding
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.actions.ClearConsoleAction
import com.intellij.execution.actions.ConsoleActionsPostProcessor
import com.intellij.execution.actions.EOFAction
import com.intellij.execution.filters.*
import com.intellij.execution.impl.ConsoleState.NotStartedStated
import com.intellij.execution.impl.ConsoleViewImpl.Companion.CONSOLE_VIEW_IN_EDITOR_VIEW
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ObservableConsoleView
import com.intellij.ide.CommonActionsManager
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.OccurenceNavigator.OccurenceInfo
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.startup.StartupManagerEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.command.undo.UndoUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.ClientEditorManager.Companion.getClientEditor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.actions.ToggleUseSoftWrapsToolbarAction
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FoldingModelEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.ContextMenuPopupHandler
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.getInstance
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.pom.Navigatable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.toolWindow.InternalDecoratorImpl.Companion.componentWithEditorBackgroundAdded
import com.intellij.toolWindow.InternalDecoratorImpl.Companion.componentWithEditorBackgroundRemoved
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.*
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.text.CharArrayUtil
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.io.IOException
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.AncestorEvent
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

open class ConsoleViewImpl protected constructor(
  project: Project,
  searchScope: GlobalSearchScope,
  viewer: Boolean,
  initialState: ConsoleState,
  usePredefinedMessageFilter: Boolean,
) : JPanel(BorderLayout()), ConsoleView, ObservableConsoleView, UiCompatibleDataProvider, OccurenceNavigator {
  @Suppress("LeakingThis")
  private val flushUserInputAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
  private val commandLineFolding = CommandLineFolding()

  private val psiDisposedCheck: DisposedPsiManagerCheck

  @JvmField
  internal val isViewer: Boolean

  @get:VisibleForTesting
  var state: ConsoleState
    private set

  @Suppress("LeakingThis")
  private val spareTimeAlarm = Alarm(this)

  @Suppress("LeakingThis")
  private val heavyAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)

  @Volatile
  private var heavyUpdateTicket = 0
  private val myPredefinedFiltersUpdateExpirableTokenProvider = ExpirableTokenProvider()
  private val myListeners: MutableCollection<ObservableConsoleView.ChangeListener> = CopyOnWriteArraySet()

  private val customActions: MutableList<AnAction> = ArrayList()

  /**
   * the text from [.print] goes there and stays there until [.flushDeferredText] is called
   * guarded by LOCK
   */
  private val myDeferredBuffer = TokenBuffer(
    if (ConsoleBuffer.useCycleBuffer() && ConsoleBuffer.getCycleBufferSize() > 0) ConsoleBuffer.getCycleBufferSize() else Int.MAX_VALUE)

  private var myUpdateFoldingsEnabled = true

  private var layeredPane: MyDiffContainer? = null
  private var myMainPanel: JPanel? = null
  private var myAllowHeavyFilters = false
  protected var myCancelStickToEnd: Boolean = false // accessed in EDT only

  @Suppress("LeakingThis")
  private val flushAlarm = Alarm(this)

  val project: Project

  private var myOutputPaused = false // guarded by LOCK

  // do not access directly, use getEditor() for proper synchronization
  private var myEditor: EditorEx? = null // guarded by LOCK

  private val LOCK = ObjectUtils.sentinel("ConsoleView lock")

  private var myHelpId: String? = null

  private val mySearchScope: GlobalSearchScope

  private val myCustomFilters: MutableList<Filter> = SmartList()

  private val myInputMessageFilter: InputFilter

  @Volatile
  @JvmField
  protected var predefinedFilters: List<Filter> = emptyList()

  constructor(project: Project, viewer: Boolean) : this(project, GlobalSearchScope.allScope(project), viewer, true)

  constructor(
    project: Project,
    searchScope: GlobalSearchScope,
    viewer: Boolean,
    usePredefinedMessageFilter: Boolean,
  ) : this(
    project = project, searchScope = searchScope, viewer = viewer,
    initialState = object : NotStartedStated() {
      override fun attachTo(console: ConsoleViewImpl, processHandler: ProcessHandler): ConsoleState {
        return ConsoleViewRunningState(console, processHandler, this, true, true)
      }
    },
    usePredefinedMessageFilter = usePredefinedMessageFilter,
  )

  private fun updatePredefinedFiltersLater(modalityState: ModalityState? = null) {
    ReadAction
      .nonBlocking<List<Filter>> {
        ConsoleViewUtil.computeConsoleFilters(
          project, this, mySearchScope)
      }
      .expireWith(this)
      .finishOnUiThread(modalityState ?: ModalityState.stateForComponent(this)
      ) { filters: List<Filter> ->
        predefinedFilters = filters
        rehighlightHyperlinksAndFoldings()
      }.submit(AppExecutorUtil.getAppExecutorService())
  }

  open val editor: Editor?
    get() {
      synchronized(LOCK) {
        return myEditor
      }
    }

  @RequiresEdt
  fun getHyperlinks(): EditorHyperlinkSupport? = editor?.let { EditorHyperlinkSupport.get(it) }

  open fun scrollToEnd() {
    ThreadingAssertions.assertEventDispatchThread()
    val editor = editor
    if (editor == null) return
    val hasSelection = editor.selectionModel.hasSelection()
    val prevSelection = if (hasSelection) editor.caretModel.caretsAndSelections else null
    scrollToEnd(editor)
    if (prevSelection != null) {
      editor.caretModel.caretsAndSelections = prevSelection
    }
  }

  private fun scrollToEnd(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    EditorUtil.scrollToTheEnd(editor, true)
    myCancelStickToEnd = false
  }

  fun foldImmediately() {
    ThreadingAssertions.assertEventDispatchThread()
    if (!flushAlarm.isEmpty) {
      cancelAllFlushRequests()
      flushDeferredText()
    }

    val editor = editor
    val model = editor!!.foldingModel
    model.runBatchFoldingOperation {
      for (region in model.allFoldRegions) {
        model.removeFoldRegion(region!!)
      }
    }

    updateFoldings(0, editor.document.lineCount - 1)
  }

  override fun attachToProcess(processHandler: ProcessHandler) {
    state = state.attachTo(this, processHandler)
  }

  override fun clear() {
    synchronized(LOCK) {
      if (editor == null) return
      // real document content will be cleared on next flush;
      myDeferredBuffer.clear()
    }
    if (!flushAlarm.isDisposed) {
      cancelAllFlushRequests()
      addFlushRequest(0, CLEAR)
      cancelHeavyAlarm()
    }
  }

  override fun scrollTo(offset: Int) {
    if (editor == null) return
    class ScrollRunnable : FlushRunnable(true) {
      public override fun doRun() {
        flushDeferredText()
        val editor = editor ?: return
        val moveOffset = getEffectiveOffset(editor)
        editor.caretModel.moveToOffset(moveOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
      }

      fun getEffectiveOffset(editor: Editor): Int {
        var moveOffset = min(offset.toDouble(), editor.document.textLength.toDouble()).toInt()
        if (ConsoleBuffer.useCycleBuffer() && moveOffset >= editor.document.textLength) {
          moveOffset = 0
        }
        return moveOffset
      }
    }
    addFlushRequest(0, ScrollRunnable())
  }

  override fun requestScrollingToEnd() {
    if (editor == null) {
      return
    }

    addFlushRequest(0, object : FlushRunnable(true) {
      public override fun doRun() {
        flushDeferredText()
        val editor = editor
        if (editor != null && !flushAlarm.isDisposed) {
          scrollToEnd(editor)
        }
      }
    })
  }

  private fun addFlushRequest(millis: Int, flushRunnable: FlushRunnable) {
    flushRunnable.queue(millis.toLong())
  }

  override fun setOutputPaused(value: Boolean) {
    synchronized(LOCK) {
      myOutputPaused = value
      if (!value && editor != null) {
        requestFlushImmediately()
      }
    }
  }

  override fun isOutputPaused(): Boolean {
    synchronized(LOCK) {
      return myOutputPaused
    }
  }

  private var keepSlashR = true
  fun setEmulateCarriageReturn(emulate: Boolean) {
    keepSlashR = emulate
  }

  override fun hasDeferredOutput(): Boolean {
    synchronized(LOCK) {
      return myDeferredBuffer.length() > 0
    }
  }

  override fun performWhenNoDeferredOutput(runnable: Runnable) {
    ThreadingAssertions.assertEventDispatchThread()
    if (!hasDeferredOutput()) {
      WriteIntentReadAction.run {
        runnable.run()
      }
      return
    }
    if (spareTimeAlarm.isDisposed) {
      return
    }
    if (layeredPane == null) {
      component
    }
    spareTimeAlarm.addRequest(
      { performWhenNoDeferredOutput(runnable) },
      100,
      ModalityState.stateForComponent(layeredPane!!)
    )
  }

  override fun getComponent(): JComponent {
    ThreadingAssertions.assertEventDispatchThread()
    if (myMainPanel == null) {
      myMainPanel = JPanel(BorderLayout())
      layeredPane = MyDiffContainer(myMainPanel!!, createCompositeFilter().updateMessage).also {
        Disposer.register(this, it)
        add(it, BorderLayout.CENTER)
      }
    }

    if (editor == null) {
      val editor = initConsoleEditor()
      synchronized(LOCK) {
        myEditor = editor
      }
      requestFlushImmediately()
      myMainPanel!!.add(createCenterComponent(), BorderLayout.CENTER)
    }
    return this
  }

  protected open fun createCompositeFilter(): CompositeFilter {
    val compositeFilter = CompositeFilter(project, ContainerUtil.concat(myCustomFilters, predefinedFilters))
    compositeFilter.setForceUseAllFilters(true)
    return compositeFilter
  }

  /**
   * Adds transparent (actually, non-opaque) component over console.
   * It will be as big as console. Use it to draw on console because it does not prevent user from console usage.
   *
   * @param component component to add
   */
  fun addLayerToPane(component: JComponent) {
    ThreadingAssertions.assertEventDispatchThread()
    getComponent() // Make sure component exists
    component.isOpaque = false
    component.isVisible = true
    layeredPane!!.add(component, null, 0)
  }

  private fun initConsoleEditor(): EditorEx {
    ThreadingAssertions.assertEventDispatchThread()
    val editor = createConsoleEditor()
    registerConsoleEditorActions(editor)
    editor.scrollPane.border = IdeBorderFactory.createBorder(SideBorder.LEFT)
    val mouseListener: MouseAdapter = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        updateStickToEndState(editor, true)
      }

      override fun mouseDragged(e: MouseEvent) {
        updateStickToEndState(editor, false)
      }

      override fun mouseWheelMoved(e: MouseWheelEvent) {
        if (e.isShiftDown) return  // ignore horizontal scrolling

        updateStickToEndState(editor, false)
      }
    }
    editor.scrollPane.addMouseWheelListener(mouseListener)
    editor.scrollPane.verticalScrollBar.addMouseListener(mouseListener)
    editor.scrollPane.verticalScrollBar.addMouseMotionListener(mouseListener)
    editor.scrollingModel.addVisibleAreaListener { e: VisibleAreaEvent ->
      // There is a possible case that the console text is populated while the console is not shown (e.g., we're debugging and
      // 'Debugger' tab is active while 'Console' is not). It's also possible that newly added text contains long lines that
      // are soft-wrapped. We want to update viewport position then when the console becomes visible.
      val oldR = e.oldRectangle
      if (oldR != null && oldR.height <= 0 && e.newRectangle.height > 0 && isStickingToEnd(
          editor)
      ) {
        scrollToEnd(editor)
      }
    }
    return editor
  }

  private fun updateStickToEndState(editor: EditorEx, useImmediatePosition: Boolean) {
    ThreadingAssertions.assertEventDispatchThread()
    val vScrollAtBottom = isVScrollAtTheBottom(editor, useImmediatePosition)
    val caretAtTheLastLine = isCaretAtTheLastLine(editor)
    if (!vScrollAtBottom && caretAtTheLastLine) {
      myCancelStickToEnd = true
    }
  }

  protected open fun createCenterComponent(): JComponent {
    ThreadingAssertions.assertEventDispatchThread()
    return editor!!.component
  }

  override fun dispose() {
    state = state.dispose()
    for (l in ancestorListeners) {
      removeAncestorListener(l)
    }
    val editor = editor
    if (editor != null) {
      cancelAllFlushRequests()
      spareTimeAlarm.cancelAllRequests()
      disposeEditor()
      editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, null)
      synchronized(LOCK) {
        myDeferredBuffer.clear()
        myEditor = null
      }
    }
  }

  private fun cancelAllFlushRequests() {
    flushAlarm.cancelAllRequests()
    CLEAR.clearRequested()
    FLUSH.clearRequested()
  }

  @TestOnly
  @RequiresEdt
  fun waitAllRequests() {
    assert(ApplicationManager.getApplication().isUnitTestMode)
    val future = ApplicationManager.getApplication().executeOnPooledThread {
      while (true) {
        try {
          flushAlarm.waitForAllExecuted(10, TimeUnit.SECONDS)
          flushUserInputAlarm.waitForAllExecuted(10, TimeUnit.SECONDS)
          flushAlarm.waitForAllExecuted(10, TimeUnit.SECONDS)
          flushUserInputAlarm.waitForAllExecuted(10, TimeUnit.SECONDS)
          return@executeOnPooledThread
        }
        catch (e: CancellationException) {
          //try again
        }
        catch (e: TimeoutException) {
          throw RuntimeException(e)
        }
      }
    }
    try {
      while (true) {
        try {
          future[10, TimeUnit.MILLISECONDS]
          break
        }
        catch (ignored: TimeoutException) {
        }
        EDT.dispatchAllInvocationEvents()
      }
    }
    catch (e: InterruptedException) {
      throw RuntimeException(e)
    }
    catch (e: ExecutionException) {
      throw RuntimeException(e)
    }
  }

  protected open fun disposeEditor() {
    UIUtil.invokeAndWaitIfNeeded {
      val editor = editor
      if (!editor!!.isDisposed) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }

  override fun print(text: String, contentType: ConsoleViewContentType) {
    val result = myInputMessageFilter.applyFilter(text, contentType)
    if (result == null) {
      print(text, contentType, null)
    }
    else {
      for (pair in result) {
        if (pair.first != null) {
          print(pair.first, (if (pair.second == null) contentType else pair.second)!!, null)
        }
      }
    }
  }

  open fun print(text: String, contentType: ConsoleViewContentType, info: HyperlinkInfo?) {
    val effectiveText = Strings.convertLineSeparators(text, keepSlashR)
    synchronized(LOCK) {
      val hasEditor = editor != null
      myDeferredBuffer.print(effectiveText, contentType, info)
      if (hasEditor) {
        if (contentType === ConsoleViewContentType.USER_INPUT) {
          requestFlushImmediately()
        }
        else {
          val shouldFlushNow = myDeferredBuffer.length() >= myDeferredBuffer.cycleBufferSize
          addFlushRequest(if (shouldFlushNow) 0 else DEFAULT_FLUSH_DELAY, FLUSH)
        }
      }
    }
  }

  // send text which was typed in the console to the running process
  private fun sendUserInput(typedText: CharSequence) {
    ThreadingAssertions.assertEventDispatchThread()
    if (state.isRunning && NEW_LINE_MATCHER.indexIn(typedText) >= 0) {
      val textToSend = ConsoleTokenUtil.computeTextToSend(editor!!, project)
      if (!textToSend.isEmpty()) {
        flushUserInputAlarm.addRequest({
                                         if (state.isRunning) {
                                           try {
                                             // this may block forever, see IDEA-54340
                                             state.sendUserInput(textToSend.toString())
                                           }
                                           catch (ignored: IOException) {
                                           }
                                         }
                                       }, 0)
      }
    }
  }

  protected open val stateForUpdate: ModalityState?
    get() = null

  private fun requestFlushImmediately() {
    addFlushRequest(0, FLUSH)
  }

  /**
   * Holds number of symbols managed by the current console.
   *
   *
   * Total number is assembled as a sum of symbols that are already pushed to the document and number of deferred symbols that
   * are awaiting to be pushed to the document.
   */
  override fun getContentSize(): Int {
    var length: Int
    var editor: Editor?
    synchronized(LOCK) {
      length = myDeferredBuffer.length()
      editor = this.editor
    }
    return (if (editor == null || CLEAR.hasRequested()) 0 else editor!!.document.textLength) + length
  }

  override fun canPause(): Boolean {
    return true
  }

  open fun flushDeferredText() {
    ThreadingAssertions.assertEventDispatchThread()
    if (isDisposed) return
    val editor = editor as EditorEx?
    val shouldStickToEnd = !myCancelStickToEnd && isStickingToEnd(
      editor!!)
    myCancelStickToEnd = false // Cancel only needs to last for one update. Next time, isStickingToEnd() will be false.

    var deferredTokens: List<TokenBuffer.TokenInfo>
    val document: Document = editor!!.document

    synchronized(LOCK) {
      if (myOutputPaused) return
      deferredTokens = myDeferredBuffer.drain()
      if (deferredTokens.isEmpty()) return
      cancelHeavyAlarm()
    }

    val lastProcessedOutput = document.createRangeMarker(document.textLength, document.textLength)

    if (!shouldStickToEnd) {
      editor.scrollingModel.accumulateViewportChanges()
    }
    val contentTypes = HashSet<ConsoleViewContentType>()
    val contents = ArrayList<Pair<String, ConsoleViewContentType>>()
    val addedText: CharSequence
    try {
      // the text can contain one "\r" at the start meaning we should delete the last line
      val startsWithCR = deferredTokens[0] == TokenBuffer.CR_TOKEN
      if (startsWithCR) {
        // remove last line if any
        if (document.lineCount != 0) {
          val lineStartOffset = document.getLineStartOffset(document.lineCount - 1)
          document.deleteString(lineStartOffset, document.textLength)
        }
      }
      val startIndex = if (startsWithCR) 1 else 0
      val refinedTokens = ArrayList<TokenBuffer.TokenInfo>(deferredTokens.size - startIndex)
      val backspacePrefixLength = ConsoleTokenUtil.evaluateBackspacesInTokens(deferredTokens, startIndex, refinedTokens)
      if (backspacePrefixLength > 0) {
        val lineCount = document.lineCount
        if (lineCount != 0) {
          val lineStartOffset = document.getLineStartOffset(lineCount - 1)
          document.deleteString(
            max(lineStartOffset.toDouble(), (document.textLength - backspacePrefixLength).toDouble()).toInt(), document.textLength)
        }
      }
      addedText = TokenBuffer.getRawText(refinedTokens)
      document.insertString(document.textLength, addedText)
      ConsoleTokenUtil.highlightTokenTextAttributes(this.editor!!, project, refinedTokens, getHyperlinks()!!, contentTypes, contents)
    }
    finally {
      if (!shouldStickToEnd) {
        editor.scrollingModel.flushViewportChanges()
      }
    }
    if (!contentTypes.isEmpty()) {
      for (each in myListeners) {
        @Suppress("removal", "DEPRECATION")
        each.contentAdded(contentTypes)
      }
    }
    if (!contents.isEmpty()) {
      for (each in myListeners) {
        for (i in contents.indices.reversed()) {
          each.textAdded(contents[i].first, contents[i].second)
        }
      }
    }
    psiDisposedCheck.performCheck()

    val startLine = if (lastProcessedOutput.isValid) editor.document.getLineNumber(lastProcessedOutput.endOffset) else 0
    lastProcessedOutput.dispose()
    highlightHyperlinksAndFoldings(startLine, myPredefinedFiltersUpdateExpirableTokenProvider.createExpirable())

    if (shouldStickToEnd) {
      scrollToEnd()
    }
    sendUserInput(addedText)
  }

  private val isDisposed: Boolean
    get() {
      val editor = editor
      return project.isDisposed || editor == null || editor.isDisposed
    }

  protected open fun doClear() {
    ThreadingAssertions.assertEventDispatchThread()

    if (isDisposed) return

    val editor = editor
    val document = editor!!.document
    val documentTextLength = document.textLength
    if (documentTextLength > 0) {
      DocumentUtil.executeInBulk(document) { document.deleteString(0, documentTextLength) }
    }
    synchronized(LOCK) {
      clearHyperlinkAndFoldings()
    }
    val model = DocumentMarkupModel.forDocument(editor.document, project, true)
    model.removeAllHighlighters() // remove all empty highlighters leftovers if any
    editor.inlayModel.getInlineElementsInRange(0, 0).forEach(
      Consumer { disposable: Inlay<*>? ->
        Disposer.dispose(
          disposable!!)
      }) // remove inlays if any
  }

  private fun clearHyperlinkAndFoldings() {
    ThreadingAssertions.assertEventDispatchThread()
    val editor = editor
    for (highlighter in editor!!.markupModel.allHighlighters) {
      if (highlighter.getUserData(ConsoleTokenUtil.MANUAL_HYPERLINK) == null) {
        editor.markupModel.removeHighlighter(highlighter)
      }
    }

    editor.foldingModel.runBatchFoldingOperation { (editor.foldingModel as FoldingModelEx).clearFoldRegions() }
    editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength).forEach(
      Consumer { inlay: Inlay<*>? ->
        Disposer.dispose(
          inlay!!)
      })

    cancelHeavyAlarm()
  }

  private fun cancelHeavyAlarm() {
    if (!heavyAlarm.isDisposed) {
      heavyAlarm.cancelAllRequests()
      ++heavyUpdateTicket
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    val editor = editor as EditorEx?
    sink.set(CommonDataKeys.EDITOR, this.editor)
    sink.set(LangDataKeys.CONSOLE_VIEW, this)
    sink.set(PlatformCoreDataKeys.HELP_ID, myHelpId)

    if (editor == null) return
    sink.set(CommonDataKeys.CARET, editor.caretModel.currentCaret)
    sink.set(PlatformDataKeys.COPY_PROVIDER, editor.copyProvider)

    val hyperlinks = getHyperlinks()
    sink.lazy<Navigatable>(CommonDataKeys.NAVIGATABLE) {
      val offset = editor.caretModel.offset
      val info = hyperlinks!!.getHyperlinkAt(offset)
      if (info == null) null
      else object : Navigatable {
        override fun navigate(requestFocus: Boolean) {
          info.navigate(project)
        }

        override fun canNavigate(): Boolean = true

        override fun canNavigateToSource(): Boolean = true
      }
    }
  }

  override fun setHelpId(helpId: String) {
    myHelpId = helpId
  }

  fun setUpdateFoldingsEnabled(updateFoldingsEnabled: Boolean) {
    myUpdateFoldingsEnabled = updateFoldingsEnabled
  }

  override fun addMessageFilter(filter: Filter) {
    myCustomFilters.add(filter)
  }

  fun clearMessageFilters() {
    myCustomFilters.clear()
  }

  override fun printHyperlink(hyperlinkText: String, info: HyperlinkInfo?) {
    print(hyperlinkText, ConsoleViewContentType.NORMAL_OUTPUT, info)
  }

  private fun createConsoleEditor(): EditorEx {
    ThreadingAssertions.assertEventDispatchThread()
    val editor = doCreateConsoleEditor()
    LOG.assertTrue(UndoUtil.isUndoDisabledFor(editor.document), "Undo must be disabled in console for performance reasons")
    LOG.assertTrue(!(editor.document as DocumentImpl).isWriteThreadOnly,
                   "Console document must support background modifications, see e.g. ConsoleViewUtil.setupConsoleEditor() $javaClass")
    editor.installPopupHandler(object : ContextMenuPopupHandler() {
      override fun getActionGroup(event: EditorMouseEvent) = getPopupGroup(event)
    })

    val bufferSize = if (ConsoleBuffer.useCycleBuffer()) ConsoleBuffer.getCycleBufferSize() else 0
    editor.document.setCyclicBufferSize(bufferSize)
    editor.document.putUserData(IS_CONSOLE_DOCUMENT, true)
    editor.putUserData(CONSOLE_VIEW_IN_EDITOR_VIEW, this)
    editor.settings.isAllowSingleLogicalLineFolding = true // We want to fold long soft-wrapped command lines
    return editor
  }

  protected open fun doCreateConsoleEditor(): EditorEx {
    return ConsoleViewUtil.setupConsoleEditor(project, true, false)
  }

  private fun registerConsoleEditorActions(editor: Editor) {
    ThreadingAssertions.assertEventDispatchThread()
    val shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_GOTO_DECLARATION).shortcuts
    val shortcutSet = CustomShortcutSet(*ArrayUtil.mergeArrays(shortcuts, CommonShortcuts.ENTER.shortcuts))
    HyperlinkNavigationAction().registerCustomShortcutSet(shortcutSet, editor.contentComponent)
    if (!isViewer) {
      registerActionHandler(editor, EOFAction.ACTION_ID)
    }
  }

  private fun getPopupGroup(event: EditorMouseEvent): ActionGroup {
    ThreadingAssertions.assertEventDispatchThread()
    val actionManager = ActionManager.getInstance()
    val info = if (getHyperlinks() != null) getHyperlinks()!!.getHyperlinkInfoByEvent(event) else null
    var group: ActionGroup? = null
    if (info is HyperlinkWithPopupMenuInfo) {
      group = info.getPopupMenuGroup(event.mouseEvent)
    }
    if (group == null) {
      group = actionManager.getAction(CONSOLE_VIEW_POPUP_MENU) as ActionGroup
    }
    val postProcessors = ConsoleActionsPostProcessor.EP_NAME.extensionList
    var result = group.getChildren(null)

    for (postProcessor in postProcessors) {
      result = postProcessor.postProcessPopupActions(this, result)
    }
    return DefaultActionGroup(*result)
  }

  private fun highlightHyperlinksAndFoldings(startLine: Int, expirableToken: Expirable) {
    ThreadingAssertions.assertEventDispatchThread()
    val compositeFilter = createCompositeFilter()
    val canHighlightHyperlinks = !compositeFilter.isEmpty

    if (!canHighlightHyperlinks && !myUpdateFoldingsEnabled) {
      return
    }
    val document = editor!!.document
    if (document.textLength == 0) return

    val endLine = max(0.0, (document.lineCount - 1).toDouble()).toInt()

    if (canHighlightHyperlinks) {
      getHyperlinks()!!.highlightHyperlinksLater(compositeFilter, startLine, endLine, expirableToken)
    }

    if (myAllowHeavyFilters && compositeFilter.isAnyHeavy && compositeFilter.shouldRunHeavy()) {
      runHeavyFilters(compositeFilter, startLine, endLine)
    }
    if (myUpdateFoldingsEnabled) {
      updateFoldings(startLine, endLine)
    }
  }

  fun invalidateFiltersExpirableTokens() {
    myPredefinedFiltersUpdateExpirableTokenProvider.invalidateAll()
  }

  open fun rehighlightHyperlinksAndFoldings() {
    ThreadingAssertions.assertEventDispatchThread()
    if (isDisposed) return
    invalidateFiltersExpirableTokens()
    clearHyperlinkAndFoldings()
    highlightHyperlinksAndFoldings(0, myPredefinedFiltersUpdateExpirableTokenProvider.createExpirable())
  }

  private fun runHeavyFilters(compositeFilter: CompositeFilter, line1: Int, endLine: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    val startLine = max(0.0, line1.toDouble()).toInt()

    val document = editor!!.document
    val startOffset = document.getLineStartOffset(startLine)
    val text = document.getText(TextRange(startOffset, document.getLineEndOffset(endLine)))
    val documentCopy: Document = DocumentImpl(text, true)
    documentCopy.setReadOnly(true)

    layeredPane!!.startUpdating()
    val currentValue = heavyUpdateTicket
    heavyAlarm.addRequest({
                            if (!compositeFilter.shouldRunHeavy()) {
                              return@addRequest
                            }

                            try {
                              compositeFilter.applyHeavyFilter(documentCopy, startOffset, startLine) { additionalHighlight ->
                                addFlushRequest(0, object : FlushRunnable(true) {
                                  public override fun doRun() {
                                    if (heavyUpdateTicket != currentValue) {
                                      return
                                    }

                                    val additionalAttributes = additionalHighlight.getTextAttributes(null)
                                    if (additionalAttributes != null) {
                                      val item = additionalHighlight.resultItems[0]
                                      getHyperlinks()!!.addHighlighter(item.highlightStartOffset, item.highlightEndOffset, additionalAttributes)
                                    }
                                    else {
                                      getHyperlinks()!!.highlightHyperlinks(additionalHighlight, 0)
                                    }
                                  }
                                })
                              }
                            }
                            catch (ignore: IndexNotReadyException) {
                            }
                            finally {
                              if (heavyAlarm.activeRequestCount <= 1) { // only the current request
                                UIUtil.invokeLaterIfNeeded { layeredPane!!.finishUpdating() }
                              }
                            }
                          }, 0)
  }

  protected open fun updateFoldings(startLine: Int, endLine: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    val editor = editor
    editor!!.foldingModel.runBatchFoldingOperation {
      val document = editor.document
      var existingRegion: FoldRegion? = null
      if (startLine > 0) {
        val prevLineStart = document.getLineStartOffset(startLine - 1)
        val regions = FoldingUtil.getFoldRegionsAtOffset(editor, prevLineStart)
        if (regions.size == 1) {
          existingRegion = regions[0]
        }
      }
      var lastFolding = if (existingRegion == null) null else findFoldingByRegion(existingRegion)
      var lastStartLine = Int.MAX_VALUE
      if (lastFolding != null) {
        val offset = existingRegion!!.startOffset
        if (offset == 0) {
          lastStartLine = 0
        }
        else {
          lastStartLine = document.getLineNumber(offset)
          if (document.getLineStartOffset(lastStartLine) != offset) lastStartLine++
        }
      }

      val extensions = ConsoleFolding.EP_NAME.extensionList.filter { it.isEnabledForConsole(this) }
      if (extensions.isEmpty()) return@runBatchFoldingOperation
      for (line in startLine..endLine) {
        /*
            Grep Console plugin allows to fold empty lines. We need to handle this case in a special way.
    
            Multiple lines are grouped into one folding, but to know when you can create the folding,
            you need a line which does not belong to that folding.
            When a new line, or a chunk of lines is printed, #addFolding is called for that lines + for an empty string
            (which basically does only one thing, gets a folding displayed).
            We do not want to process that empty string, but also we do not want to wait for another line
            which will create and display the folding - we'd see an unfolded stacktrace until another text came and flushed it.
            Thus, the condition: the last line(empty string) should still flush, but not be processed by
            com.intellij.execution.ConsoleFolding.
             */
        val next = if (line < endLine) foldingForLine(extensions, line, document) else null
        if (next !== lastFolding) {
          if (lastFolding != null) {
            var isExpanded = false
            if (line > startLine && existingRegion != null && lastStartLine < startLine) {
              isExpanded = existingRegion.isExpanded
              editor.foldingModel.removeFoldRegion(existingRegion)
            }
            addFoldRegion(document, lastFolding, lastStartLine, line - 1, isExpanded)
          }
          lastFolding = next
          lastStartLine = line
          existingRegion = null
        }
      }
    }
  }

  private fun addFoldRegion(document: Document, folding: ConsoleFolding, startLine: Int, endLine: Int, isExpanded: Boolean) {
    val toFold: MutableList<String> = ArrayList(endLine - startLine + 1)
    for (i in startLine..endLine) {
      toFold.add(EditorHyperlinkSupport.getLineText(document, i, false))
    }

    var oStart = document.getLineStartOffset(startLine)
    if (oStart > 0 && folding.shouldBeAttachedToThePreviousLine()) oStart--
    val oEnd = CharArrayUtil.shiftBackward(document.immutableCharSequence, document.getLineEndOffset(endLine) - 1, " \t") + 1

    val placeholder = folding.getPlaceholderText(project, toFold)
    val region = if (placeholder == null) null else editor!!.foldingModel.addFoldRegion(oStart, oEnd, placeholder)
    if (region != null) {
      region.isExpanded = isExpanded
      region.putUserData(USED_FOLDING_FQN_KEY, getFoldingFqn(folding))
    }
  }

  private fun findFoldingByRegion(region: FoldRegion): ConsoleFolding? {
    val lastFoldingFqn = USED_FOLDING_FQN_KEY[region]
    if (lastFoldingFqn == null) return null
    val consoleFolding = ConsoleFolding.EP_NAME.getByKey(lastFoldingFqn,
                                                         ConsoleViewImpl::class.java
    ) { consoleFolding: ConsoleFolding -> getFoldingFqn(consoleFolding) }
    return if (consoleFolding != null && consoleFolding.isEnabledForConsole(this)) consoleFolding else null
  }

  private fun foldingForLine(extensions: List<ConsoleFolding>, line: Int, document: Document): ConsoleFolding? {
    val lineText = EditorHyperlinkSupport.getLineText(document, line, false)
    if (line == 0 && commandLineFolding.shouldFoldLine(project, lineText)) {
      return commandLineFolding
    }

    for (extension in extensions) {
      if (extension.shouldFoldLine(project, lineText)) {
        return extension
      }
    }

    return null
  }

  private class ClearThisConsoleAction(private val myConsoleView: ConsoleView) : ClearConsoleAction() {
    override fun update(e: AnActionEvent) {
      val enabled = myConsoleView.contentSize > 0
      e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
      myConsoleView.clear()
    }
  }

  @Deprecated("use {@link ClearConsoleAction} instead")
  class ClearAllAction : ClearConsoleAction()

  open fun type(editor: Editor, text: String) {
    ThreadingAssertions.assertEventDispatchThread()
    flushDeferredText()
    val selectionModel = editor.selectionModel

    val lastOffset = if (selectionModel.hasSelection()) selectionModel.selectionStart else editor.caretModel.offset - 1
    val marker = ConsoleTokenUtil.findTokenMarker(this.editor!!, project, lastOffset)
    if (marker == null || ConsoleTokenUtil.getTokenType(marker) !== ConsoleViewContentType.USER_INPUT) {
      print(text, ConsoleViewContentType.USER_INPUT)
      flushDeferredText()
      moveScrollRemoveSelection(editor, editor.document.textLength)
      return
    }

    val textToUse = StringUtil.convertLineSeparators(text)
    val typeOffset: Int
    val document = editor.document
    if (selectionModel.hasSelection()) {
      val start = selectionModel.selectionStart
      val end = selectionModel.selectionEnd
      document.deleteString(start, end)
      selectionModel.removeSelection()
      typeOffset = start
      assert(
        typeOffset <= document.textLength) { "typeOffset=" + typeOffset + "; document.getTextLength()=" + document.textLength + "; sel start=" + start + "; sel end=" + end + "; document=" + document.javaClass }
    }
    else {
      typeOffset = editor.caretModel.offset
      assert(
        typeOffset <= document.textLength) { "typeOffset=" + typeOffset + "; document.getTextLength()=" + document.textLength + "; caret model=" + editor.caretModel }
    }
    insertUserText(editor, typeOffset, textToUse)
  }

  internal abstract class ConsoleActionHandler(private val originalHandler: EditorActionHandler) : EditorActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
      ThreadingAssertions.assertEventDispatchThread()
      val console = getRunningConsole(dataContext)
      if (console != null) {
        execute(console, editor, dataContext)
      }
      else {
        originalHandler.execute(editor, caret, dataContext)
      }
    }

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
      val console = getRunningConsole(dataContext)
      return console != null || originalHandler.isEnabled(editor, caret, dataContext)
    }

    protected abstract fun execute(console: ConsoleViewImpl, editor: Editor, context: DataContext)

    companion object {
      private fun getRunningConsole(context: DataContext): ConsoleViewImpl? {
        val editor = CommonDataKeys.EDITOR.getData(context)
        if (editor != null) {
          val console = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW)
          if (console != null && console.state.isRunning && !console.isViewer) {
            return console
          }
        }
        return null
      }
    }
  }

  internal class EnterHandler(originalHandler: EditorActionHandler) : ConsoleActionHandler(originalHandler) {
    override fun execute(console: ConsoleViewImpl, editor: Editor, context: DataContext) {
      console.print("\n", ConsoleViewContentType.USER_INPUT)
      console.flushDeferredText()
      moveScrollRemoveSelection(editor, editor.document.textLength)
    }
  }

  internal class PasteHandler(originalHandler: EditorActionHandler) : ConsoleActionHandler(originalHandler) {
    override fun execute(console: ConsoleViewImpl, editor: Editor, context: DataContext) {
      val text = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
      if (text == null) return
      console.type(editor, text)
    }
  }

  internal open class DeleteBackspaceHandler(
    originalHandler: EditorActionHandler,
    private val myTextOffsetToDeleteRelativeToCaret: Int,
    private val myParentActionId: String,
  ) : ConsoleActionHandler(originalHandler) {
    override fun execute(console: ConsoleViewImpl, editor: Editor, context: DataContext) {
      if (IncrementalSearchHandler.isHintVisible(editor)) {
        getDefaultActionHandler(myParentActionId).execute(editor, null, context)
        return
      }

      console.flushDeferredText()
      val document = editor.document
      val length = document.textLength
      if (length == 0) {
        return
      }

      val selectionModel = editor.selectionModel
      if (selectionModel.hasSelection()) {
        console.deleteUserText(selectionModel.selectionStart,
                               selectionModel.selectionEnd - selectionModel.selectionStart)
      }
      else {
        val offset = editor.caretModel.offset + myTextOffsetToDeleteRelativeToCaret
        if (offset >= 0) {
          console.deleteUserText(offset, 1)
        }
      }
    }

    companion object {
      private fun getDefaultActionHandler(actionId: String): EditorActionHandler {
        return EditorActionManager.getInstance().getActionHandler(actionId)
      }
    }
  }

  internal class BackspaceHandler(originalHandler: EditorActionHandler) : DeleteBackspaceHandler(originalHandler, -1,
                                                                                                 IdeActions.ACTION_EDITOR_BACKSPACE)

  internal class DeleteHandler(originalHandler: EditorActionHandler) : DeleteBackspaceHandler(originalHandler, 0,
                                                                                              IdeActions.ACTION_EDITOR_DELETE)

  internal class TabHandler(originalHandler: EditorActionHandler) : ConsoleActionHandler(originalHandler) {
    override fun execute(console: ConsoleViewImpl, editor: Editor, context: DataContext) {
      console.type(console.editor!!, "\t")
    }
  }

  override fun getPreferredFocusableComponent(): JComponent {
    //ensure editor created
    component
    return editor!!.contentComponent
  }


  // navigate up/down in stack trace
  override fun hasNextOccurence(): Boolean {
    return calcNextOccurrence(1) != null
  }

  override fun hasPreviousOccurence(): Boolean {
    return calcNextOccurrence(-1) != null
  }

  override fun goNextOccurence(): OccurenceInfo? {
    return calcNextOccurrence(1)
  }

  protected open fun calcNextOccurrence(delta: Int): OccurenceInfo? {
    val editor = editor
    if (isDisposed || editor == null) {
      return null
    }

    return EditorHyperlinkSupport.getNextOccurrence(editor, delta) { next: RangeHighlighter ->
      val offset = next.startOffset
      scrollTo(offset)
      val hyperlinkInfo = EditorHyperlinkSupport.getHyperlinkInfo(next)
      if (hyperlinkInfo is BrowserHyperlinkInfo) {
        return@getNextOccurrence
      }
      if (hyperlinkInfo is HyperlinkInfoBase) {
        val position = editor.offsetToVisualPosition(offset)
        val point = editor.visualPositionToXY(
          VisualPosition(position.getLine() + 1, position.getColumn()))
        hyperlinkInfo.navigate(project, RelativePoint(editor.contentComponent, point))
      }
      else hyperlinkInfo?.navigate(project)
    }
  }

  override fun goPreviousOccurence(): OccurenceInfo? {
    return calcNextOccurrence(-1)
  }

  override fun getNextOccurenceActionName(): String {
    return ExecutionBundle.message("down.the.stack.trace")
  }

  override fun getPreviousOccurenceActionName(): String {
    return ExecutionBundle.message("up.the.stack.trace")
  }

  fun addCustomConsoleAction(action: AnAction) {
    customActions.add(action)
  }

  override fun createConsoleActions(): Array<AnAction> {
    //Initializing prev and next occurrences actions
    val actionsManager = CommonActionsManager.getInstance()
    val prevAction = actionsManager.createPrevOccurenceAction(this)
    prevAction.templatePresentation.setText(previousOccurenceActionName)
    val nextAction = actionsManager.createNextOccurenceAction(this)
    nextAction.templatePresentation.setText(nextOccurenceActionName)

    val switchSoftWrapsAction: AnAction = object : ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.CONSOLE) {
      override fun getEditor(e: AnActionEvent): Editor? {
        val editor = this@ConsoleViewImpl.editor
        return if (editor == null) null else getClientEditor(editor, currentOrNull)
      }
    }
    val autoScrollToTheEndAction: AnAction = ScrollToTheEndToolbarAction(
      editor)

    val consoleActions: MutableList<AnAction> = ArrayList()
    consoleActions.add(prevAction)
    consoleActions.add(nextAction)
    consoleActions.add(switchSoftWrapsAction)
    consoleActions.add(autoScrollToTheEndAction)
    consoleActions.add(ActionManager.getInstance().getAction("Print"))
    consoleActions.add(clearThisConsoleAction())
    consoleActions.addAll(customActions)
    val postProcessors = ConsoleActionsPostProcessor.EP_NAME.extensionList
    var result = consoleActions.toTypedArray()
    for (postProcessor in postProcessors) {
      result = postProcessor.postProcess(this, result)
    }
    return result
  }

  protected fun clearThisConsoleAction(): AnAction {
    return ClearThisConsoleAction(this)
  }

  override fun allowHeavyFilters() {
    myAllowHeavyFilters = true
  }

  override fun addChangeListener(listener: ObservableConsoleView.ChangeListener, parent: Disposable) {
    myListeners.add(listener)
    Disposer.register(parent) { myListeners.remove(listener) }
  }

  override fun addNotify() {
    super.addNotify()
    componentWithEditorBackgroundAdded(this)
  }

  override fun removeNotify() {
    super.removeNotify()
    componentWithEditorBackgroundRemoved(this)
  }

  private fun insertUserText(editor: Editor, offset: Int, text: String) {
    @Suppress("NAME_SHADOWING")
    var offset = offset
    ThreadingAssertions.assertEventDispatchThread()
    val result = myInputMessageFilter.applyFilter(text, ConsoleViewContentType.USER_INPUT)
    if (result == null) {
      doInsertUserInput(editor, offset, text)
    }
    else {
      for (pair in result) {
        val chunkText = pair.getFirst()
        val chunkType = pair.getSecond()
        if (chunkType == ConsoleViewContentType.USER_INPUT) {
          doInsertUserInput(editor, offset, chunkText)
          offset += chunkText.length
        }
        else {
          print(chunkText, chunkType, null)
        }
      }
    }
  }

  private fun doInsertUserInput(editor: Editor, offset: Int, text: String) {
    ThreadingAssertions.assertEventDispatchThread()
    val document = editor.document

    val oldDocLength = document.textLength
    document.insertString(offset, text)
    val newStartOffset = max(0.0,
                             (document.textLength - oldDocLength + offset - text.length).toDouble()).toInt() // take care of trim document
    val newEndOffset = document.textLength - oldDocLength + offset // take care of trim document

    if (ConsoleTokenUtil.findTokenMarker(this.editor!!, project, newEndOffset) == null) {
      ConsoleTokenUtil.createTokenRangeHighlighter(this.editor!!, project, ConsoleViewContentType.USER_INPUT, newStartOffset, newEndOffset,
                                                   text != "\n")
    }

    moveScrollRemoveSelection(editor, newEndOffset)
    sendUserInput(text)
  }

  private fun deleteUserText(startOffset: Int, length: Int) {
    ThreadingAssertions.assertEventDispatchThread()
    val editor = editor
    val document = editor!!.document

    val marker = ConsoleTokenUtil.findTokenMarker(this.editor!!, project, startOffset)
    if (marker == null || ConsoleTokenUtil.getTokenType(marker) !== ConsoleViewContentType.USER_INPUT) {
      return
    }

    val endOffset = startOffset + length
    if (startOffset >= 0 && endOffset >= 0 && endOffset > startOffset) {
      document.deleteString(startOffset, endOffset)
    }
    moveScrollRemoveSelection(editor, startOffset)
  }

  open val isRunning: Boolean
    get() = state.isRunning

  fun addNotificationComponent(notificationComponent: JComponent) {
    ThreadingAssertions.assertEventDispatchThread()
    add(notificationComponent, BorderLayout.NORTH)
  }

  /**
   * Command line used to launch application/test from idea may be quite long.
   * Hence, it takes many visual lines during representation if soft wraps are enabled
   * or, otherwise, takes many columns and makes horizontal scrollbar thumb too small.
   *
   *
   * Our point is to fold such a long command line and represent it as a single visual line by default.
   */
  private inner class CommandLineFolding : ConsoleFolding() {
    override fun shouldFoldLine(project: Project, line: String): Boolean {
      return line.length >= 1000 && state.isCommandLine(line)
    }

    override fun getPlaceholderText(project: Project, lines: List<String>): String {
      val text = lines[0]

      var index = 0
      if (text[0] == '"') {
        index = text.indexOf('"', 1) + 1
      }
      if (index == 0) {
        var nonWhiteSpaceFound = false
        while (index < text.length) {
          val c = text[index]
          if (c != ' ' && c != '\t') {
            nonWhiteSpaceFound = true
            index++
            continue
          }
          if (nonWhiteSpaceFound) {
            break
          }
          index++
        }
      }
      assert(index <= text.length)
      return text.substring(0, index) + " ..."
    }
  }

  private open inner class FlushRunnable(
    // true if requests of this class should not be merged (i.e., they can be requested multiple times)
    private val adHoc: Boolean,
  ) : Runnable {
    // Does request of this class was myFlushAlarm.addRequest()-ed but not yet executed
    private val requested = AtomicBoolean()

    fun queue(delay: Long) {
      if (flushAlarm.isDisposed) return
      if (adHoc || requested.compareAndSet(false, true)) {
        flushAlarm.addRequest(this, delay, stateForUpdate)
      }
    }

    fun clearRequested() {
      requested.set(false)
    }

    fun hasRequested(): Boolean {
      return requested.get()
    }

    override fun run() {
      if (isDisposed) {
        return
      }

      // flush requires UndoManger/CommandProcessor properly initialized
      if (!StartupManagerEx.getInstanceEx(project).startupActivityPassed()) {
        addFlushRequest(DEFAULT_FLUSH_DELAY, FLUSH)
      }

      clearRequested()
      WriteIntentReadAction.run {
        doRun()
      }
    }

    protected open fun doRun() {
      flushDeferredText()
    }
  }

  private val FLUSH = FlushRunnable(false)

  private inner class ClearRunnable : FlushRunnable(false) {
    public override fun doRun() {
      doClear()
    }
  }

  private val CLEAR = ClearRunnable()

  init {
    initTypedHandler()
    isViewer = viewer
    state = initialState
    psiDisposedCheck = DisposedPsiManagerCheck(project)
    this.project = project
    mySearchScope = searchScope

    myInputMessageFilter = ConsoleViewUtil.computeInputFilter(this, project, searchScope)
    project.messageBus.connect(
      this).subscribe<DumbService.DumbModeListener>(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
      private var myLastStamp: Long = 0

      override fun enteredDumbMode() {
        val editor: Editor = editor ?: return
        myLastStamp = editor.document.modificationStamp
      }

      override fun exitDumbMode() {
        ApplicationManager.getApplication().invokeLater {
          val editor = editor
          if (editor == null || project.isDisposed || getInstance(project).isDumb) {
            return@invokeLater
          }

          val document = editor.document
          if (myLastStamp != document.modificationStamp) {
            rehighlightHyperlinksAndFoldings()
          }
        }
      }
    })
    @Suppress("LeakingThis")
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe<EditorColorsListener>(EditorColorsManager.TOPIC, EditorColorsListener { `__`: EditorColorsScheme? ->
        ThreadingAssertions.assertEventDispatchThread()
        if (isDisposed) {
          return@EditorColorsListener
        }

        ConsoleTokenUtil.updateAllTokenTextAttributes(editor!!, project)
      })
    if (usePredefinedMessageFilter) {
      if (!isCurrentlyUnderLocalId && predefinedFilters.isEmpty()) {
        updatePredefinedFiltersLater(ModalityState.defaultModalityState())
      }
      @Suppress("LeakingThis")
      addAncestorListener(object : AncestorListenerAdapter() {
        override fun ancestorAdded(event: AncestorEvent) {
          if (predefinedFilters.isEmpty()) {
            updatePredefinedFiltersLater()
          }
        }
      })
      ApplicationManager.getApplication().messageBus.connect(this)
        .subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
          override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
            updatePredefinedFiltersLater()
          }

          override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
            updatePredefinedFiltersLater()
          }
        })
    }
  }

  private inner class HyperlinkNavigationAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      val runnable: Runnable = checkNotNull(getHyperlinks()!!.getLinkNavigationRunnable(editor!!.getCaretModel().logicalPosition))
      runnable.run()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = getHyperlinks()!!.getLinkNavigationRunnable(editor!!.getCaretModel().logicalPosition) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  val text: String
    get() = editor!!.document.text

  companion object {
    private const val CONSOLE_VIEW_POPUP_MENU: @NonNls String = "ConsoleView.PopupMenu"
    private val LOG = logger<ConsoleViewImpl>()

    private val DEFAULT_FLUSH_DELAY = SystemProperties.getIntProperty("console.flush.delay.ms", 200)

    @JvmField
    val CONSOLE_VIEW_IN_EDITOR_VIEW: Key<ConsoleViewImpl> = Key.create("CONSOLE_VIEW_IN_EDITOR_VIEW")
    @JvmField
    val IS_CONSOLE_DOCUMENT: Key<Boolean> = Key.create("IS_CONSOLE_DOCUMENT")

    fun isStickingToEnd(editor: EditorEx): Boolean {
      return isCaretAtTheLastLine(editor) || isVScrollAtTheBottom(editor, true)
    }
  }
}

private var ourTypedHandlerInitialized = false
private val NEW_LINE_MATCHER: CharMatcher = CharMatcher.anyOf("\n\r")

private fun initTypedHandler() {
  if (ourTypedHandlerInitialized) return
  EditorActionManager.getInstance()
  val typedAction = TypedAction.getInstance()
  @Suppress("DEPRECATION")
  typedAction.setupHandler(MyTypedHandler(typedAction.handler))
  ourTypedHandlerInitialized = true
}

private fun isCaretAtTheLastLine(editor: Editor): Boolean {
  val document = editor.document
  val caretOffset = editor.caretModel.offset
  return document.getLineNumber(caretOffset) >= document.lineCount - 1
}

private fun isVScrollAtTheBottom(editor: EditorEx, useImmediatePosition: Boolean): Boolean {
  val scrollBar = editor.scrollPane.verticalScrollBar
  val scrollBarPosition = if (useImmediatePosition) scrollBar.value else editor.scrollingModel.visibleAreaOnScrollingFinished.y
  return scrollBarPosition == scrollBar.maximum - scrollBar.visibleAmount
}

@RequiresEdt
private fun registerActionHandler(editor: Editor, @Suppress("SameParameterValue") actionId: String) {
  val action = ActionManager.getInstance().getAction(actionId)
  action.registerCustomShortcutSet(action.shortcutSet, editor.contentComponent)
}

private val USED_FOLDING_FQN_KEY = Key.create<String>("USED_FOLDING_KEY")

private fun getFoldingFqn(consoleFolding: ConsoleFolding): String = consoleFolding.javaClass.name

private fun moveScrollRemoveSelection(editor: Editor, offset: Int) {
  ThreadingAssertions.assertEventDispatchThread()
  editor.caretModel.moveToOffset(offset)
  editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
  editor.selectionModel.removeSelection()
}

private class MyTypedHandler(originalAction: TypedActionHandler) : TypedActionHandlerBase(originalAction) {
  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    val consoleView = editor.getUserData(CONSOLE_VIEW_IN_EDITOR_VIEW)
    if (consoleView == null || !consoleView.state.isRunning || consoleView.isViewer) {
      myOriginalHandler?.execute(editor, charTyped, dataContext)
      return
    }
    val text = charTyped.toString()
    consoleView.type(editor, text)
  }
}