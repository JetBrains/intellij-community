// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.*
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.requests.ContentDiffRequest
import com.intellij.diff.tools.combined.search.CombinedDiffSearchContext
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.ScrollingModelImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.JBColor
import com.intellij.ui.ListenerUtil
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.cancelOnDispose
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.min
import kotlin.math.roundToInt

@ApiStatus.Internal
class CombinedDiffViewer(
  private val context: DiffContext,
  blockListener: BlockListener,
  private val blockState: BlockState,
  private val viewState: CombinedDiffUIState
) : CombinedDiffNavigation,
    CombinedDiffCaretNavigation,
    UiDataProvider,
    Disposable {
  private val project = context.project!! // CombinedDiffContext expected

  @OptIn(DelicateCoroutinesApi::class)
  private val cs = GlobalScope.childScope("CombinedDiffViewer", Dispatchers.EDT)

  private val diffViewers: MutableMap<CombinedBlockId, DiffViewer> = hashMapOf()
  private val diffBlocks: MutableMap<CombinedBlockId, CombinedCollapsibleDiffBlock<*>> = hashMapOf()

  private val collapsedDiffBlocks: BitSet = BitSet(blockState.blocksCount)

  private val blocksPanel: CombinedDiffBlocksPanel = CombinedDiffBlocksPanel(blockState) { (blockId, blockHeight) ->
    if (blockId.isCollapsed) blockHeight else maxOf(blockHeight, scrollPane.viewport.height)
  }

  private val scrollPane: JBScrollPane = object : JBScrollPane(
    blocksPanel,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      DataSink.uiDataSnapshot(sink, this@CombinedDiffViewer)
    }
  }.apply {
    border = JBUI.Borders.empty()
    viewportBorder = JBUI.Borders.customLineTop(CombinedDiffUI.MAIN_HEADER_BACKGROUND)
    viewport.addChangeListener(ViewportChangeListener())
  }

  private val stickyHeaderPanel: Wrapper = Wrapper().apply {
    isOpaque = true
  }

  private val separatorPanel = JPanel(null)

  private val contentPanel: JComponent = object : JBLayeredPane() {
    override fun getPreferredSize(): Dimension = scrollPane.preferredSize

    override fun doLayout() {
      scrollPane.setBounds(0, 0, width, height)
      separatorPanel.setBounds(0, 0, width, 1)
    }
  }.apply {
    isFocusable = false
    add(scrollPane, JLayeredPane.DEFAULT_LAYER, 0)
    add(stickyHeaderPanel, JLayeredPane.POPUP_LAYER, 1)
    add(separatorPanel, JLayeredPane.POPUP_LAYER, 0)
  }

  private val scrollSupport = CombinedDiffScrollSupport(project, this)

  private val focusListener = FocusListener(this)

  private val blockListeners = EventDispatcher.create(BlockListener::class.java)

  private fun updateDiffInfo(blockId: CombinedBlockId) {
    val request = (diffViewers[blockId] as? DiffViewerBase)?.request
    if (request !is ContentDiffRequest) {
      viewState.setDiffInfo(CombinedDiffUIState.DiffInfoState.Empty)
      return
    }

    val titles = request.contentTitles.filter { it != null && it.isNotBlank() }
    val newDiffInfo = when (titles.size) {
      0 -> CombinedDiffUIState.DiffInfoState.Empty
      1 -> CombinedDiffUIState.DiffInfoState.SingleTitle(titles[0])
      else -> CombinedDiffUIState.DiffInfoState.TwoTitles(titles[0], titles[1])
    }
    viewState.setDiffInfo(newDiffInfo)
  }

  private val combinedEditorSettingsAction =
    CombinedEditorSettingsActionGroup(TextDiffViewerUtil.getTextSettings(context), ::foldingModels, ::editors)

  private val visibleBlocksUpdateQueue =
    MergingUpdateQueue("CombinedDiffViewer.visibleBlocksUpdateQueue", 100, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD)
      .also { Disposer.register(this, it) }

  init {
    blockState.addListener({ old, new -> changeSelection(old, new) }, this)
    blockListeners.listeners.add(blockListener)
    selectDiffBlock(blockState.currentBlock, true)

    cs.launch {
      viewState.separatorState.collect { visible ->
        separatorPanel.background = if (visible) JBColor.border() else CombinedDiffUI.MAIN_HEADER_BACKGROUND
      }
    }.cancelOnDispose(this)
  }

  internal fun updateBlockContent(newContent: CombinedDiffBlockContent) {
    val blockId = newContent.blockId
    val newDiffBlock = createDiffBlock(newContent, blockId.isCollapsed)

    newDiffBlock.updateBlockContent(newContent)
    val newViewer = newContent.viewer
    configureEditorForCombinedDiff(newViewer)
    scrollSupport.setupEditorsScrollingListener(newViewer)
    installCombinedDiffViewer(newViewer, this)

    runPreservingViewportContent(scrollPane, blocksPanel) {
      disposeDiffBlockIfPresent(blockId)
      registerNewDiffBlock(blockId, newDiffBlock, newViewer)

      blocksPanel.setContent(blockId, newDiffBlock.component)
      newDiffBlock.component.validate()
      newViewer.init()

      updateDiffInfo(blockState.currentBlock)
      if (newViewer !is CombinedDiffLoadingBlock) {
        val requestFocus = context.removeUserData(COMBINED_DIFF_VIEWER_INITIAL_FOCUS_REQUEST) == true
        if (requestFocus && blockState.currentBlock == blockId) {
          requestFocusInDiffViewer(blockId)
        }
      }
    }

    if (blockState.currentBlock == blockId) {
      scrollToFirstChange(blockId, false, ScrollPolicy.SCROLL_TO_CARET)
    }
  }

  internal fun replaceBlockWithPlaceholder(blockId: CombinedBlockId) {
    runPreservingViewportContent(scrollPane, blocksPanel) {
      val viewer = diffViewers[blockId]
      val diffBlock = diffBlocks[blockId]
      val height = diffBlock?.body?.size?.height ?: viewer?.component?.size?.height
      blocksPanel.setPlaceholder(blockId, height)
      disposeDiffBlockIfPresent(blockId)
    }
  }

  private fun createDiffBlock(content: CombinedDiffBlockContent, isCollapsed: Boolean): CombinedCollapsibleDiffBlock<*> {
    val viewer = content.viewer
    if (!viewer.isEditorBased) {
      focusListener.register(viewer.component, this)
    }
    val diffBlockFactory = CombinedSimpleDiffBlockFactory()
    val newDiffBlock = diffBlockFactory.createBlock(project, content, isCollapsed)
    newDiffBlock.addListener(MyCombinedBlockListener(), this)

    installActionOnBlock("Vcs.CombinedDiff.CaretToPrevBlock", newDiffBlock)
    installActionOnBlock("Vcs.CombinedDiff.CaretToNextBlock", newDiffBlock)
    installActionOnBlock("Vcs.CombinedDiff.ToggleCollapseBlock", newDiffBlock, CommonShortcuts.ENTER)

    return newDiffBlock
  }

  private fun registerNewDiffBlock(blockId: CombinedBlockId,
                                   newBlock: CombinedCollapsibleDiffBlock<*>,
                                   newViewer: DiffViewer) {
    Disposer.register(newBlock, Disposable {
      diffBlocks.remove(blockId)
      diffViewers.remove(blockId)?.also(Disposer::dispose)
    })
    Disposer.register(this, newBlock)

    diffBlocks[blockId] = newBlock
    if (newBlock.id == blockState.currentBlock) {
      //initial selection of current block
      changeSelection(blockState.currentBlock, newBlock.id)
    }
    diffViewers[blockId] = newViewer
  }

  private fun disposeDiffBlockIfPresent(blockId: CombinedBlockId) {
    val oldDiffBlock = diffBlocks[blockId]
    if (oldDiffBlock != null) {
      Disposer.dispose(oldDiffBlock)
    }
  }

  val component get(): JComponent = contentPanel

  val preferredFocusedComponent get(): JComponent? = getCurrentDiffViewer()?.preferredFocusedComponent

  fun init(): FrameDiffTool.ToolbarComponents {
    val components = FrameDiffTool.ToolbarComponents()
    components.toolbarActions = createToolbarActions()
    components.needTopToolbarBorder = true
    return components
  }

  fun rediff() = diffViewers.forEach { (it as? DiffViewerBase)?.rediff() }

  override fun dispose() {}

  private val currentDiffIterable: CombinedDiffScrollSupport.CombinedDiffPrevNextDifferenceIterable
    get() = scrollSupport.currentPrevNextIterable

  override fun uiDataSnapshot(sink: DataSink) {
    val diffViewer = getCurrentDiffViewer()
    sink[CommonDataKeys.PROJECT] = project
    sink[DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE] = currentDiffIterable
    sink[DiffDataKeys.NAVIGATABLE] = (diffViewer as? DiffViewerEx)?.navigatable
    sink[DiffDataKeys.DIFF_VIEWER] = diffViewer
    sink[COMBINED_DIFF_VIEWER] = this
    sink[DiffDataKeys.CURRENT_EDITOR] = diffViewer?.currentEditor
  }

  fun getMainUI() = context.getUserData(COMBINED_DIFF_MAIN_UI)!!

  private inner class ViewportChangeListener : ChangeListener {

    override fun stateChanged(e: ChangeEvent) {
      updateStickyHeader()

      visibleBlocksUpdateQueue.queue(object : Update(e) {
        override fun run() = notifyVisibleBlocksChanged()
        override fun canEat(update: Update): Boolean = true
      })
    }
  }

  override fun canGoNextDiff(): Boolean = isNavigationEnabled() && (currentDiffIterable.canGoNext() || canGoNextBlock())
  override fun canGoPrevDiff(): Boolean = isNavigationEnabled() && (currentDiffIterable.canGoPrev() || canGoPrevBlock())

  override fun goNextDiff() {
    when {
      !blockState.currentBlock.isCollapsed && currentDiffIterable.canGoNext() -> {
        currentDiffIterable.goNext()
      }
      canGoNextBlock() -> {
        blockState.goNext()
        currentDiffIterable.goFirst()
        selectDiffBlock(blockState.currentBlock, ScrollPolicy.SCROLL_TO_BLOCK)
      }
    }
  }

  override fun goPrevDiff() {
    when {
      !blockState.currentBlock.isCollapsed && currentDiffIterable.canGoPrev() -> {
        currentDiffIterable.goPrev()
      }
      canGoPrevBlock() -> {
        blockState.goPrev()
        currentDiffIterable.goLast()
        selectDiffBlock(blockState.currentBlock, ScrollPolicy.SCROLL_TO_BLOCK)
      }
    }
  }

  override fun canGoNextBlock(): Boolean = blockState.canGoNext()
  override fun canGoPrevBlock(): Boolean = blockState.canGoPrev()

  override fun goNextBlock() {
    if (!canGoNextBlock()) return
    blockState.goNext()
    selectDiffBlock(blockState.currentBlock, ScrollPolicy.SCROLL_TO_BLOCK)
    if (!blockState.currentBlock.isCollapsed) {
      getCurrentDiffViewer()?.currentEditor?.let { EditorActionUtil.moveCaretToTextStart(it, null) }
    }
  }

  override fun goPrevBlock() {
    if (!canGoPrevBlock()) return
    blockState.goPrev()
    selectDiffBlock(blockState.currentBlock, ScrollPolicy.SCROLL_TO_BLOCK)
    if (!blockState.currentBlock.isCollapsed) {
      getCurrentDiffViewer()?.currentEditor?.let { EditorActionUtil.moveCaretToTextStart(it, null) }
    }
  }

  private fun isNavigationEnabled(): Boolean = diffBlocks.isNotEmpty()

  private fun notifyVisibleBlocksChanged() {
    val delta = CombinedDiffRegistry.getPreloadedBlocksCount()
    val viewRect = scrollPane.viewport.viewRect

    if (viewRect.height == 0) {
      return
    }

    val beforeViewport = arrayOfNulls<CombinedBlockId>(delta)
    val afterViewport = arrayOfNulls<CombinedBlockId>(delta)
    val blocksInViewport = arrayListOf<CombinedBlockId>()
    val hiddenBlocks = arrayListOf<CombinedBlockId>()
    var intersectionStarted = false

    for ((index, block) in blocksPanel.getBlockBounds().withIndex()) {
      val viewportIntersected = viewRect.intersects(block)
      val id = block.blockId

      if (!intersectionStarted && viewportIntersected) {
        intersectionStarted = true
      }

      when {
        !intersectionStarted -> {
          beforeViewport[index.mod(delta)]?.let(hiddenBlocks::add)
          beforeViewport[index.mod(delta)] = id
        }
        viewportIntersected -> blocksInViewport.add(id)
        afterViewport.any(Objects::isNull) -> afterViewport[index.mod(delta)] = id
        else -> hiddenBlocks.add(id)
      }
    }

    processBlocksState(blocksInViewport, beforeViewport.filterNotNull(), afterViewport.filterNotNull(), hiddenBlocks)
  }

  private fun processBlocksState(inViewport: ArrayList<CombinedBlockId>,
                                 beforeViewport: List<CombinedBlockId>,
                                 afterViewport: List<CombinedBlockId>,
                                 hidden: ArrayList<CombinedBlockId>) {
    if (hidden.isNotEmpty()) {
      blockListeners.multicaster.blocksHidden(hidden)
    }

    val totalVisible = inViewport + afterViewport + beforeViewport
    if (totalVisible.isNotEmpty()) {
      blockListeners.multicaster.blocksVisible(totalVisible)
      if (context.getUserData(DISABLE_LOADING_BLOCKS) == true) {
        return
      }

      totalVisible.forEach {
        if (diffViewers[it] != null) return
        val height = blocksPanel.getHeightForBlock(it)
        val content = CombinedDiffBlockContent(CombinedDiffLoadingBlock(Dimension(0, height)), it)
        updateBlockContent(content)
      }
    }
  }

  private fun updateStickyHeader() {
    val viewRect = scrollPane.viewport.viewRect
    val bounds = blocksPanel.getBlockBounds().firstOrNull { viewRect.intersects(it) } ?: return
    val block = diffBlocks[bounds.blockId]
    viewState.setStickyHeaderUnderBorder(false)

    if (block == null || block.id.isCollapsed || bounds.minY > viewRect.minY) {
      stickyHeaderPanel.setContent(null)
      stickyHeaderPanel.isVisible = false
      stickyHeaderPanel.repaint()
      return
    }

    stickyHeaderPanel.isVisible = true

    val stickyHeader = block.stickyHeaderComponent

    val headerHeight = block.header.height
    val headerHeightInViewport = min(block.component.bounds.maxY.toInt() - viewRect.bounds.minY.toInt(), headerHeight)
    val stickyHeaderY = headerHeightInViewport - headerHeight

    stickyHeaderPanel.setContent(stickyHeader)
    stickyHeaderPanel.setBounds(JBUIScale.scale(CombinedDiffUI.LEFT_RIGHT_INSET),
                                stickyHeaderY + separatorPanel.height, block.component.width,
                                headerHeight)
    stickyHeaderPanel.repaint()

    val showBorder = headerHeightInViewport < headerHeight
    if (showBorder) {
      viewState.setStickyHeaderUnderBorder(true)
    }

    block.updateBorder(updateStickyHeaderBottomBorder = showBorder)

    updateDiffInfo(block.id)
  }

  fun getCurrentBlockId(): CombinedBlockId = blockState.currentBlock

  fun getDiffBlocksCount(): Int = blockState.blocksCount

  fun getCurrentDiffViewer(): DiffViewer? = diffViewers[blockState.currentBlock]

  internal fun getDiffViewerForId(id: CombinedBlockId): DiffViewer? = diffViewers[id]

  fun selectDiffBlock(blockId: CombinedBlockId,
                      focusBlock: Boolean,
                      scrollPolicy: ScrollPolicy? = ScrollPolicy.SCROLL_TO_BLOCK) {

    selectDiffBlock(blockId, scrollPolicy, focusBlock)
  }

  fun scrollToFirstChange(blockId: CombinedBlockId,
                          focusBlock: Boolean,
                          scrollPolicy: ScrollPolicy? = ScrollPolicy.SCROLL_TO_BLOCK) {
    if (blockId.isCollapsed) {
      selectDiffBlock(blockId, scrollPolicy, false, animated = false)
    }
    else {
      selectDiffBlock(blockId, scrollPolicy, false, animated = false, ScrollType.RELATIVE)
      currentDiffIterable.goFirst(ScrollType.RELATIVE, animated = false)
      scrollSupport.scroll(ScrollPolicy.SCROLL_TO_CARET, blockId, animated = false, ScrollType.CENTER_DOWN)
    }
  }

  private fun selectDiffBlock(blockId: CombinedBlockId,
                              scrollPolicy: ScrollPolicy? = null,
                              focusBlock: Boolean = true,
                              animated: Boolean = true,
                              scrollType: ScrollType = ScrollType.CENTER,
                              editorToFocus: Editor? = null) {
    val doSelect = {
      blockState.currentBlock = blockId
      scrollSupport.scroll(scrollPolicy, blockId, animated = animated, scrollType = scrollType)
    }

    if (!focusBlock) {
      doSelect()
      return
    }

    if (blockId.isCollapsed) {
      requestFocusInBlock(blockId)
    }
    else {
      requestFocusInDiffViewer(blockId, editorToFocus)
    }
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(doSelect)
  }

  private fun changeSelection(oldBlockId: CombinedBlockId, newBlockId: CombinedBlockId) {
    if (oldBlockId != newBlockId) {
      diffBlocks[oldBlockId]?.setSelected(false)
    }
    diffBlocks[newBlockId]?.setSelected(true)
  }

  private fun requestFocusInBlock(blockId: CombinedBlockId) {
    val block = diffBlocks[blockId]
    val componentToFocus = block?.component ?: return

    val focusManager = IdeFocusManager.getInstance(project)
    if (focusManager.focusOwner != componentToFocus) {
      focusManager.requestFocus(componentToFocus, true)
    }
  }

  private fun requestFocusInDiffViewer(blockId: CombinedBlockId, editor: Editor? = null) {
    val viewer = diffViewers[blockId] ?: return
    val componentToFocus =
      with(viewer) {
        when {
          editor != null -> editor.contentComponent
          isEditorBased -> currentEditor?.contentComponent
          preferredFocusedComponent != null -> preferredFocusedComponent
          else -> component
        }
      }
    val focusManager = IdeFocusManager.getInstance(project)
    if (focusManager.focusOwner != componentToFocus && componentToFocus != null)
      focusManager.requestFocus(componentToFocus, true)
  }

  private fun createToolbarActions(): List<AnAction> {
    return listOf(combinedEditorSettingsAction)
  }

  internal fun contentChanged() {
    combinedEditorSettingsAction.installGutterPopup()
    combinedEditorSettingsAction.applyDefaults()
    updateSearch() //as a possible optimization, this should be done after all requests were loaded
  }

  private fun updateSearch() {
    getMainUI().updateSearch(createSearchContext())
  }

  private val foldingModels: List<FoldingModelSupport>
    get() = diffViewers.values.mapNotNull { viewer ->
      when (viewer) {
        is SimpleDiffViewer -> viewer.foldingModel
        is UnifiedDiffViewer -> viewer.foldingModel
        else -> null
      }
    }

  override fun moveCaretToPrevBlock() {
    blockState.goPrev()

    moveCaretToBlock(false)
  }

  override fun moveCaretToNextBlock() {
    blockState.goNext()

    moveCaretToBlock(true)
  }

  private fun moveCaretToBlock(next: Boolean) {
    val currentBlockId = blockState.currentBlock

    if (currentBlockId.isCollapsed) {
      val viewRect = scrollPane.viewport.viewRect
      val viewportIntersected =
        blocksPanel.getBlockBounds().any { currentBlockId == it.blockId && viewRect.intersects(it) }
      if (viewportIntersected) {
        requestFocusInBlock(currentBlockId)
      }
      else {
        selectDiffBlock(currentBlockId, true)
      }
    }
    else {
      val editor = getCurrentDiffViewer()?.currentEditor ?: return
      if (next) EditorActionUtil.moveCaretToTextStart(editor, null) else EditorActionUtil.moveCaretToTextEnd(editor, null)
      requestFocusInDiffViewer(blockState.currentBlock)
      scrollToCaret()
    }
  }

  internal fun toggleBlockCollapse() {
    val block = diffBlocks[blockState.currentBlock] ?: return
    val newCollapseState = !block.id.isCollapsed
    block.setCollapsed(newCollapseState)
  }

  internal fun collapseAllBlocks() {
    for (block in diffBlocks.values) {
      block.setCollapsed(true)
    }

    collapsedDiffBlocks.set(0, collapsedDiffBlocks.size() - 1, true)
  }

  private fun installActionOnBlock(actionId: String, block: CombinedDiffBlock<*>, shortcut: ShortcutSet? = null) {
    val wrap = ActionUtil.wrap(actionId)
    wrap.registerCustomShortcutSet(shortcut ?: wrap.shortcutSet, block.component)
  }

  override fun moveCaretPageUp() = movePageUpDown(pageUp = true)

  override fun moveCaretPageDown() = movePageUpDown(pageUp = false)

  private fun movePageUpDown(pageUp: Boolean) {
    if (blockState.currentBlock.isCollapsed) {
      if (pageUp) goPrevBlock() else goNextBlock()
      return
    }

    val editor = getCurrentDiffViewer()?.currentEditor ?: return
    val caretModel = editor.caretModel

    val caretPositionBeforeJump = caretModel.currentCaret.visualPosition

    if (pageUp) {
      EditorActionUtil.moveCaretPageUp(editor, false)
    } else {
      EditorActionUtil.moveCaretPageDown(editor, false)
    }

    val caretPositionAfterJump = caretModel.currentCaret.visualPosition

    if (caretPositionBeforeJump != caretPositionAfterJump) {
      scrollToCaret()
      return
    }

    if (pageUp) {
      if (canGoPrevBlock()) {
        moveCaretToPrevBlock()
      }
    } else {
      if (canGoNextBlock()) {
        moveCaretToNextBlock()
      }
    }
  }

  fun scrollToCaret() {
    scrollSupport.combinedEditorsScrollingModel.scrollToCaret(ScrollType.RELATIVE)
  }

  fun scrollToEditor(editor: Editor, focus: Boolean) {
    val entry = diffViewers.entries.find { editor in it.value.editors } ?: return
    val blockId = entry.key

    if (blockId != getCurrentBlockId()) {
      selectDiffBlock(blockId, ScrollPolicy.SCROLL_TO_BLOCK, focusBlock = focus, editorToFocus = editor)
    }
  }

  fun createSearchContext(): CombinedDiffSearchContext {
    return CombinedDiffSearchContext(blockState.iterateBlocks()
                                       .asSequence()
                                       .filterNot { id -> id.isCollapsed }
                                       .mapNotNull { id -> diffViewers[id]?.editors }
                                       .map(CombinedDiffSearchContext::EditorHolder)
                                       .toList())
  }

  internal val editors: List<Editor>
    get() = diffViewers.values.flatMap { it.editors }

  private inner class FocusListener(disposable: Disposable) : FocusAdapter(), FocusChangeListener {
    init {
      (EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx)?.addFocusChangeListener(this, disposable)
    }

    override fun focusGained(editor: Editor) {
      val blockId = diffViewers.entries.find { it.value.editors.contains(editor) }?.key ?: return

      blockState.currentBlock = blockId
    }

    override fun focusGained(e: FocusEvent) {
      val blockId = diffViewers.entries.find {
        val diffViewer = it.value
        !diffViewer.isEditorBased && (diffViewer.preferredFocusedComponent == e.component || diffViewer.component == e.component)
      }?.key ?: return

      blockState.currentBlock = blockId
    }

    fun register(component: JComponent, disposable: Disposable) {
      ListenerUtil.addFocusListener(component, this)
      Disposer.register(disposable) { ListenerUtil.removeFocusListener(component, this) }
    }
  }

  private inner class MyCombinedBlockListener : CombinedDiffBlockListener {
    override fun onCollapseStateChanged(id: CombinedBlockId, collapseState: Boolean) {
      collapsedDiffBlocks.set(blockState.indexOf(id), collapseState)

      val isStickyHeader = (stickyHeaderPanel.targetComponent != null
                            && stickyHeaderPanel.targetComponent == diffBlocks[id]?.stickyHeaderComponent)
      if (isStickyHeader) {
        selectDiffBlock(id, ScrollPolicy.SCROLL_TO_BLOCK)
      }
      else {
        if (collapseState) {
          requestFocusInBlock(id)
        }
        else {
          requestFocusInDiffViewer(id)
        }
      }

      updateSearch()
    }
  }

  enum class ScrollPolicy {
    SCROLL_TO_BLOCK,
    SCROLL_TO_CARET
  }

  private val CombinedBlockId.isCollapsed
    get() = collapsedDiffBlocks.get(blockState.indexOf(this))

  private class CombinedDiffScrollSupport(project: Project?, private val viewer: CombinedDiffViewer) {

    val currentPrevNextIterable = CombinedDiffPrevNextDifferenceIterable()

    val combinedEditorsScrollingModel = ScrollingModelImpl(CombinedEditorsScrollingModelHelper(project, viewer))

    fun setupEditorsScrollingListener(newViewer: DiffViewer) {
      newViewer.editors.forEach { editor ->
        (editor.scrollingModel as? ScrollingModelImpl)
          ?.addScrollRequestListener({ _, scrollType ->
                                       if (editor in viewer.getCurrentDiffViewer()?.editors.orEmpty()) {
                                         //e.g., scroll to caret emitted from editor search session (next/prev occurence action)
                                         combinedEditorsScrollingModel.scrollToCaret(scrollType)
                                       }
                                     }, newViewer)
      }
    }

    fun scroll(scrollPolicy: ScrollPolicy?,
               combinedBlockId: CombinedBlockId,
               animated: Boolean = true,
               scrollType: ScrollType = ScrollType.CENTER) {
      val isEditorBased = viewer.getDiffViewerForId(combinedBlockId)?.isEditorBased ?: false
      if (scrollPolicy == ScrollPolicy.SCROLL_TO_BLOCK || !isEditorBased) {
        scrollToDiffBlock(combinedBlockId)
      }
      else if (scrollPolicy == ScrollPolicy.SCROLL_TO_CARET) {
        scrollToDiffChangeWithCaret(animated, scrollType)
      }
    }

    private fun scrollToDiffChangeWithCaret(animated: Boolean = true, scrollType: ScrollType = ScrollType.CENTER) {
      if (!viewer.getCurrentDiffViewer().isEditorBased) return //avoid scrolling for non editor based viewers

      if (!animated) combinedEditorsScrollingModel.disableAnimation()
      combinedEditorsScrollingModel.scrollToCaret(scrollType)
      if (!animated) combinedEditorsScrollingModel.enableAnimation()
    }

    private fun scrollToDiffBlock(id: CombinedBlockId) {
      val bounds = viewer.blocksPanel.getBoundsForBlock(id)
      viewer.blocksPanel.scrollRectToVisible(Rectangle(0, bounds.minY, viewer.component.width, Int.MAX_VALUE))
    }

    inner class CombinedDiffPrevNextDifferenceIterable : PrevNextDifferenceIterable {
      private fun CombinedDiffViewer.getDifferencesIterable(): PrevNextDifferenceIterable? {
        val currentViewer = getCurrentDiffViewer() ?: return null
        return (currentViewer as? DiffViewerEx)?.differenceIterable
      }

      override fun canGoNext(): Boolean {
        return viewer.getDifferencesIterable()?.canGoNext() == true
      }

      override fun canGoPrev(): Boolean {
        return viewer.getDifferencesIterable()?.canGoPrev() == true
      }

      override fun goNext() {
        viewer.getDifferencesIterable()?.goNext()
        scrollToDiffChangeWithCaret()
      }

      override fun goPrev() {
        viewer.getDifferencesIterable()?.goPrev()
        scrollToDiffChangeWithCaret()
      }

      fun goFirst(scrollType: ScrollType = ScrollType.CENTER, animated: Boolean = true) {
        val diffIterable = viewer.getDifferencesIterable() ?: return
        while (diffIterable.canGoPrev()) diffIterable.goPrev()
        scrollToDiffChangeWithCaret(scrollType = scrollType, animated = animated)
      }

      fun goLast() {
        val diffIterable = viewer.getDifferencesIterable() ?: return
        while (diffIterable.canGoNext()) diffIterable.goNext()
        scrollToDiffChangeWithCaret()
      }
    }

    private inner class CombinedEditorsScrollingModelHelper(project: Project?, disposable: Disposable) :
      ScrollingModel.Supplier, ScrollingModel.ScrollingHelper, Disposable {

      private val dummyEditor: Editor //needed for ScrollingModelImpl initialization

      init {
        dummyEditor = DiffUtil.createEditor(EditorFactory.getInstance().createDocument(""), project, true, true)
        Disposer.register(disposable, this)
      }

      override fun getEditor(): Editor = viewer.getCurrentDiffViewer()?.currentEditor ?: dummyEditor

      override fun getScrollPane(): JScrollPane = viewer.scrollPane

      override fun getScrollingHelper(): ScrollingModel.ScrollingHelper = this

      override fun calculateScrollingLocation(editor: Editor, pos: VisualPosition): Point {
        val targetLocationInEditor = editor.visualPositionToXY(pos)
        return SwingUtilities.convertPoint(editor.component, targetLocationInEditor, scrollPane.viewport.view)
      }

      override fun calculateScrollingLocation(editor: Editor, pos: LogicalPosition): Point {
        val targetLocationInEditor = editor.logicalPositionToXY(pos)
        return SwingUtilities.convertPoint(editor.component, targetLocationInEditor, scrollPane.viewport.view)
      }

      override fun dispose() {
        EditorFactory.getInstance().releaseEditor(dummyEditor)
      }
    }
  }
}

private fun runPreservingViewportContent(scroll: JBScrollPane, blocksPanel: CombinedDiffBlocksPanel, run: () -> Unit) {
  val viewRect = scroll.viewport.viewRect

  var anchorBlock: BlockBounds? = null
  var diff = 0
  var isTopBoundAnchor = false

  for (block in blocksPanel.getBlockBounds()) {
    val minY = block.minY
    val maxY = block.maxY

    if (!viewRect.intersects(block)) {
      // full block before the viewport
      continue
    }

    if (minY == viewRect.minY.toInt()) {
      anchorBlock = block
      isTopBoundAnchor = true
      break
    }

    if (maxY >= viewRect.minY && maxY <= viewRect.maxY) {
      // the bottom of block in the viewport
      anchorBlock = block
      diff = (maxY - viewRect.minY).roundToInt()
      break
    }

    if (maxY > viewRect.maxY && minY < viewRect.minY) {
      // this block is larger than the viewport
      anchorBlock = block
      isTopBoundAnchor = true
      diff = (minY - viewRect.minY).toInt()
      break
    }
  }

  run()

  if (anchorBlock == null) return

  val newViewRect = scroll.viewport.viewRect
  val newBlockRect = blocksPanel.getBlockBounds().first { it.blockId == anchorBlock.blockId }

  newViewRect.y = if (isTopBoundAnchor) newBlockRect.minY else newBlockRect.maxY
  newViewRect.y -= diff
  scroll.viewport.viewPosition = Point(newViewRect.x, newViewRect.y)
}

val DiffViewer.currentEditor: Editor?
  get() = when (this) {
    is EditorDiffViewer -> currentEditor
    else -> null
  }

internal val DiffViewer.editors: List<Editor>
  get() = when (this) {
    is EditorDiffViewer -> editors
    else -> emptyList()
  }

private val DiffViewer?.isEditorBased: Boolean
  get() = this is EditorDiffViewer

private fun configureEditorForCombinedDiff(viewer: DiffViewer) {
  removeAdditionalLines(viewer)
  disableAdditionalPageAtBottom(viewer)
}

private fun removeAdditionalLines(viewer: DiffViewer) {
  viewer.editors.forEach { editor ->
    editor.settings.additionalLinesCount = 0
    (editor as? EditorImpl)?.resetSizes()
  }
}

private fun disableAdditionalPageAtBottom(viewer: DiffViewer) {
  for (editor in viewer.editors) {
    editor.settings.isAdditionalPageAtBottom = false
  }
}

private fun installCombinedDiffViewer(newViewer: DiffViewer, combinedDiffViewer: CombinedDiffViewer) {
  newViewer.editors.forEach { it.putUserData(COMBINED_DIFF_VIEWER_KEY, combinedDiffViewer) }
}

private fun Rectangle.intersects(bb: BlockBounds): Boolean =
  (bb.minY >= minY && bb.minY < maxY) ||
  (bb.maxY > minY && bb.maxY <= maxY) ||
  (bb.minY <= minY && bb.maxY >= maxY)

@ApiStatus.Experimental
interface BlockListener : EventListener {
  fun blocksHidden(blockIds: Collection<CombinedBlockId>)
  fun blocksVisible(blockIds: Collection<CombinedBlockId>)
}
