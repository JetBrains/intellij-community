// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.impl.ui.DiffInfo
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.ScrollingModelImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBColor
import com.intellij.ui.ListenerUtil
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.NonNls
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

class CombinedDiffViewer(
  private val context: DiffContext,
  keys: List<CombinedBlockId>,
  blockToSelect: CombinedBlockId?,
  blockListener: BlockListener,
) : DiffViewer,
    CombinedDiffNavigation,
    CombinedDiffCaretNavigation,
    DataProvider {
  private val project = context.project!! // CombinedDiffContext expected

  private val blockState = BlockState(keys, blockToSelect ?: keys.first())

  private val diffViewers: MutableMap<CombinedBlockId, DiffViewer> = hashMapOf()
  private val diffBlocks: MutableMap<CombinedBlockId, CombinedDiffBlock<*>> = hashMapOf()

  private val blocksPanel: CombinedDiffBlocksPanel = CombinedDiffBlocksPanel(blockState) { pref ->
    maxOf(pref, scrollPane.viewport.height)
  }

  private val scrollPane: JBScrollPane = JBScrollPane(
    blocksPanel,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ).apply {
    DataManager.registerDataProvider(this, this@CombinedDiffViewer)
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

  private val diffInfo = object : DiffInfo() {
    private var currentIndex: Int = 0
    private val currentBlock get() = blockState[currentIndex] ?: getCurrentBlockId()

    fun updateForBlock(blockId: CombinedBlockId) {
      val newIndex = blockState.indexOf(blockId)
      if (currentIndex != newIndex) {
        currentIndex = newIndex
        update()
      }
    }

    override fun getContentTitles(): List<String?> {
      return currentBlock.let { blockId -> diffViewers[blockId] as? DiffViewerBase }?.request?.contentTitles ?: return emptyList()
    }
  }

  private val combinedEditorSettingsAction =
    CombinedEditorSettingsAction(TextDiffViewerUtil.getTextSettings(context), ::foldingModels, ::editors)

  private val visibleBlocksUpdateQueue =
    MergingUpdateQueue("CombinedDiffViewer.visibleBlocksUpdateQueue", 100, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD)
      .also { Disposer.register(this, it) }

  init {
    blockListeners.listeners.add(blockListener)
    selectDiffBlock(blockState.currentBlock, true)
  }

  internal fun updateBlockContent(newContent: CombinedDiffBlockContent) {
    val createDiffBlock = createDiffBlock(newContent)
    createDiffBlock.updateBlockContent(newContent)
    val newViewer = newContent.viewer
    configureEditorForCombinedDiff(newViewer)
    scrollSupport.setupEditorsScrollingListener(newViewer)
    installCombinedDiffViewer(newViewer, this)

    val blockId = newContent.blockId
    diffBlocks[blockId] = createDiffBlock
    runPreservingViewportContent(scrollPane, blocksPanel) {
      diffViewers.remove(blockId)?.also(Disposer::dispose)
      diffViewers[blockId] = newViewer
      blocksPanel.setContent(blockId, createDiffBlock.component)
      createDiffBlock.component.validate()
      newViewer.init()

      diffInfo.update()
      val requestFocus = DiffUtil.isUserDataFlagSet(COMBINED_DIFF_VIEWER_INITIAL_FOCUS_REQUEST, context)
      if (requestFocus && blockState.currentBlock == blockId) {
        requestFocusInDiffViewer(blockId)
      }
    }
    if (blockState.currentBlock == blockId) {
      scrollToFirstChange(blockId, false, ScrollPolicy.SCROLL_TO_CARET)
    }
  }

  internal fun replaceBlockWithPlaceholder(blockId: CombinedBlockId) {
    runPreservingViewportContent(scrollPane, blocksPanel) {
      val viewer = diffViewers.remove(blockId)?.also(Disposer::dispose)
      val size = viewer?.component?.size?.height
      blocksPanel.setPlaceholder(blockId, size)
    }
  }

  private fun createDiffBlock(content: CombinedDiffBlockContent): CombinedDiffBlock<*> {
    val viewer = content.viewer
    if (!viewer.isEditorBased) {
      focusListener.register(viewer.component, this)
    }

    val diffBlockFactory = CombinedSimpleDiffBlockFactory()

    val diffBlock = diffBlockFactory.createBlock(project, content)
    val blockId = diffBlock.id
    Disposer.register(diffBlock, Disposable {
      diffBlocks.remove(blockId)
      diffViewers.remove(blockId)?.also(Disposer::dispose)
    })
    Disposer.register(this, diffBlock)

    return diffBlock
  }

  override fun getComponent(): JComponent = contentPanel

  override fun getPreferredFocusedComponent(): JComponent? = getCurrentDiffViewer()?.preferredFocusedComponent

  override fun init(): FrameDiffTool.ToolbarComponents {
    val components = FrameDiffTool.ToolbarComponents()
    components.toolbarActions = createToolbarActions()
    components.diffInfo = diffInfo
    components.needTopToolbarBorder = true
    return components
  }

  fun rediff() = diffViewers.forEach { (it as? DiffViewerBase)?.rediff() }

  override fun dispose() {}

  private val currentDiffIterable: CombinedDiffScrollSupport.CombinedDiffPrevNextDifferenceIterable
    get() = scrollSupport.currentPrevNextIterable

  override fun getData(dataId: @NonNls String): Any? {
    if (CommonDataKeys.PROJECT.`is`(dataId)) return project
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.`is`(dataId)) return currentDiffIterable
    if (DiffDataKeys.NAVIGATABLE.`is`(dataId)) return getCurrentDataProvider()?.let(DiffDataKeys.NAVIGATABLE::getData)
    if (DiffDataKeys.DIFF_VIEWER.`is`(dataId)) return getCurrentDiffViewer()
    if (COMBINED_DIFF_VIEWER.`is`(dataId)) return this
    if (DiffDataKeys.CURRENT_EDITOR.`is`(dataId)) getCurrentDiffViewer()?.currentEditor

    return null
  }

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
      currentDiffIterable.canGoNext() -> {
        currentDiffIterable.goNext()
      }
      canGoNextBlock() -> {
        blockState.goNext()
        currentDiffIterable.goFirst()
        requestFocusInDiffViewer(blockState.currentBlock)
      }
    }
  }

  override fun goPrevDiff() {
    when {
      currentDiffIterable.canGoPrev() -> {
        currentDiffIterable.goPrev()
      }
      canGoPrevBlock() -> {
        blockState.goPrev()
        currentDiffIterable.goLast()
        requestFocusInDiffViewer(blockState.currentBlock)
      }
    }
  }

  override fun canGoNextBlock(): Boolean = blockState.canGoNext()
  override fun canGoPrevBlock(): Boolean = blockState.canGoPrev()

  override fun goNextBlock() {
    if (!canGoNextBlock()) return
    blockState.goNext()
    selectDiffBlock(blockState.currentBlock, ScrollPolicy.SCROLL_TO_BLOCK)
    getCurrentDiffViewer()?.currentEditor?.let { EditorActionUtil.moveCaretToTextStart(it, null) }
  }

  override fun goPrevBlock() {
    if (!canGoPrevBlock()) return
    blockState.goPrev()
    selectDiffBlock(blockState.currentBlock, ScrollPolicy.SCROLL_TO_BLOCK)
    getCurrentDiffViewer()?.currentEditor?.let { EditorActionUtil.moveCaretToTextStart(it, null) }
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
        val height = blocksPanel.getBoundsForBlock(it).height
        val content = CombinedDiffBlockContent(CombinedDiffLoadingBlock(Dimension(0, height)), it)
        updateBlockContent(content)
      }
    }
  }

  private fun updateStickyHeader() {
    val viewRect = scrollPane.viewport.viewRect
    val bounds = blocksPanel.getBlockBounds().firstOrNull { viewRect.intersects(it) } ?: return
    val block = diffBlocks[bounds.blockId]
    separatorPanel.background = CombinedDiffUI.MAIN_HEADER_BACKGROUND

    if (block == null || bounds.minY > viewRect.minY) {
      stickyHeaderPanel.setContent(null)
      stickyHeaderPanel.isVisible = false
      stickyHeaderPanel.repaint()
      return
    }

    stickyHeaderPanel.isVisible = true

    val stickyHeader = block.stickyHeader

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
      separatorPanel.background = JBColor.border()
    }

    diffInfo.updateForBlock(block.id)
  }

  fun getCurrentBlockId(): CombinedBlockId = blockState.currentBlock

  fun getDiffBlocksCount(): Int = blockState.blocksCount

  private fun getCurrentDiffViewer(): DiffViewer? = diffViewers[blockState.currentBlock]

  internal fun getDiffViewerForId(id: CombinedBlockId): DiffViewer? = diffViewers[id]

  fun selectDiffBlock(blockId: CombinedBlockId,
                      focusBlock: Boolean,
                      scrollPolicy: ScrollPolicy? = ScrollPolicy.SCROLL_TO_BLOCK) {

    selectDiffBlock(blockId, scrollPolicy, focusBlock)
  }

  fun scrollToFirstChange(blockId: CombinedBlockId,
                          focusBlock: Boolean,
                          scrollPolicy: ScrollPolicy? = ScrollPolicy.SCROLL_TO_BLOCK) {
    selectDiffBlock(blockId, scrollPolicy, false, animated = false, ScrollType.RELATIVE)
    currentDiffIterable.goFirst(ScrollType.RELATIVE, animated = false)
    scrollSupport.scroll(ScrollPolicy.SCROLL_TO_CARET, blockId, animated = false, ScrollType.CENTER_DOWN)
  }

  private fun selectDiffBlock(blockId: CombinedBlockId,
                              scrollPolicy: ScrollPolicy? = null,
                              focusBlock: Boolean = true,
                              animated: Boolean = true,
                              scrollType: ScrollType = ScrollType.CENTER) {
    val doSelect = {
      blockState.currentBlock = blockId
      scrollSupport.scroll(scrollPolicy, blockId, animated = animated, scrollType = scrollType)
    }

    if (!focusBlock) {
      doSelect()
      return
    }
    requestFocusInDiffViewer(blockId)
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(doSelect)
  }

  private fun requestFocusInDiffViewer(blockId: CombinedBlockId) {
    val viewer = diffViewers[blockId] ?: return
    val componentToFocus =
      with(viewer) {
        when {
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
  }

  private val foldingModels: List<FoldingModelSupport>
    get() = diffViewers.values.mapNotNull { viewer ->
      when (viewer) {
        is SimpleDiffViewer -> viewer.foldingModel
        is UnifiedDiffViewer -> viewer.foldingModel
        else -> null
      }
    }

  private fun getCurrentDataProvider(): DataProvider? {
    val currentDiffViewer = getCurrentDiffViewer()
    if (currentDiffViewer is DataProvider) {
      return currentDiffViewer
    }

    return currentDiffViewer?.let(DiffViewer::getComponent)?.let(DataManager::getDataProvider)
  }

  override fun moveCaretToPrevBlock() {
    blockState.goPrev()
    val editor = getCurrentDiffViewer()?.currentEditor ?: return
    EditorActionUtil.moveCaretToTextEnd(editor, null)
    requestFocusInDiffViewer(blockState.currentBlock)
    scrollToCaret()
  }

  override fun moveCaretToNextBlock() {
    blockState.goNext()
    val editor = getCurrentDiffViewer()?.currentEditor ?: return
    EditorActionUtil.moveCaretToTextStart(editor, null)
    requestFocusInDiffViewer(blockState.currentBlock)
    scrollToCaret()
  }

  override fun moveCaretPageUp() = movePageUpDown(pageUp = true)

  override fun moveCaretPageDown() = movePageUpDown(pageUp = false)

  private fun movePageUpDown(pageUp: Boolean) {
    // move viewport in the new position
    val viewRect = scrollPane.viewport.viewRect

    val pageHeightWithoutStickyHeader = viewRect.height - stickyHeaderPanel.height
    val editor = getCurrentDiffViewer()?.currentEditor ?: return
    val lineHeight = editor.lineHeight
    val pageOffset = (if (pageUp) -pageHeightWithoutStickyHeader else pageHeightWithoutStickyHeader) / lineHeight * lineHeight

    val maxNewY = scrollPane.viewport.view.height - 1
    viewRect.y = (viewRect.y + pageOffset).coerceAtLeast(0).coerceAtMost(maxNewY)

    scrollPane.viewport.viewPosition = Point(viewRect.x, viewRect.y)

    // move caret
    val visualPositionInCurrentEditor = editor.caretModel.visualPosition
    val pointInCurrentEditor = editor.visualPositionToXY(visualPositionInCurrentEditor)
    val pointInView = SwingUtilities.convertPoint(editor.component, pointInCurrentEditor, scrollPane.viewport.view)

    val newPointInView = Point(pointInView.x, (pointInView.y + pageOffset).coerceAtLeast(0).coerceAtMost(maxNewY))
    val newComponent = scrollPane.viewport.view.getComponentAt(newPointInView)
    if (newComponent is CombinedSimpleDiffBlock) {
      selectDiffBlock(newComponent.id, true, null)
      val newEditor = getCurrentDiffViewer()?.currentEditor ?: return
      val pointInNewEditor = SwingUtilities.convertPoint(scrollPane.viewport.view, newPointInView, newEditor.component)
      val visualPositionInNewEditor = newEditor.xyToVisualPosition(pointInNewEditor)
      newEditor.caretModel.moveToVisualPosition(visualPositionInNewEditor)
      requestFocusInDiffViewer(blockState.currentBlock)
    }
    scrollToCaret()
  }

  fun scrollToCaret() {
    scrollSupport.combinedEditorsScrollingModel.scrollToCaret(ScrollType.RELATIVE)
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

  enum class ScrollPolicy {
    SCROLL_TO_BLOCK,
    SCROLL_TO_CARET
  }

  private class CombinedDiffScrollSupport(project: Project?, private val viewer: CombinedDiffViewer) {

    val currentPrevNextIterable = CombinedDiffPrevNextDifferenceIterable()

    val combinedEditorsScrollingModel = ScrollingModelImpl(CombinedEditorsScrollingModelHelper(project, viewer))

    fun setupEditorsScrollingListener(newViewer: DiffViewer) {
      newViewer.editors.forEach { editor ->
        (editor.scrollingModel as? ScrollingModelImpl)
          ?.addScrollRequestListener({ _, scrollType ->
                                       combinedEditorsScrollingModel.scrollToCaret(scrollType)
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
        return getCurrentDataProvider()?.let(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE::getData)
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

private val DiffViewer.currentEditor: Editor?
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

interface BlockListener : EventListener {
  fun blocksHidden(blockIds: Collection<CombinedBlockId>)
  fun blocksVisible(blockIds: Collection<CombinedBlockId>)
}

internal interface BlockOrder {
  fun iterateBlocks(): Iterable<CombinedBlockId>

  val blocksCount: Int
}

private class BlockState(list: List<CombinedBlockId>, current: CombinedBlockId) : PrevNextDifferenceIterable, BlockOrder {
  private val blocks: List<CombinedBlockId> = list.toList()

  private val blockByIndex: MutableMap<CombinedBlockId, Int> = mutableMapOf()

  var currentBlock: CombinedBlockId = current

  init {
    blocks.forEachIndexed { index, block ->
      blockByIndex[block] = index
    }
    // todo: find and fix initial problem in Space review integration
    if (!blocks.contains(current)) {
      currentBlock = blocks.first()
    }
  }

  fun indexOf(blockId: CombinedBlockId): Int = blockByIndex[blockId]!!

  operator fun get(index: Int): CombinedBlockId? = if (index in blocks.indices) blocks[index] else null

  override val blocksCount: Int
    get() = blocks.size

  override fun iterateBlocks(): Iterable<CombinedBlockId> = blocks.asIterable()

  override fun canGoPrev(): Boolean = currentIndex > 0

  override fun canGoNext(): Boolean = currentIndex < blocksCount - 1

  override fun goPrev() {
    currentBlock = blocks[this.currentIndex - 1]
  }

  override fun goNext() {
    currentBlock = blocks[this.currentIndex + 1]
  }

  private val currentIndex: Int
    get() = indexOf(currentBlock)
}
