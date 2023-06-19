// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.impl.ui.DiffInfo
import com.intellij.diff.tools.binary.OnesideBinaryDiffViewer
import com.intellij.diff.tools.binary.ThreesideBinaryDiffViewer
import com.intellij.diff.tools.binary.TwosideBinaryDiffViewer
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actions.EditorActionUtil
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.ScrollingModelImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ListenerUtil
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import java.awt.Point
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.*
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.min
import kotlin.math.roundToInt

class CombinedDiffViewer(
  context: DiffContext
) : DiffViewer,
    CombinedDiffNavigation,
    CombinedDiffCaretNavigation,
    DataProvider {
  private val project = context.project!! // CombinedDiffContext expected

  private val stubPanelAfterBlock: JPanel = object : JPanel(null) {
    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      preferredSize.width = parent.width
      preferredSize.height = 0

      if (parent.componentCount > 1) {
        val lastBlockHeight = parent.components[getDiffBlocksCount() - 1].height
        val viewportHeight = scrollPane.viewport.height
        if (viewportHeight > lastBlockHeight) {
          preferredSize.height = viewportHeight - lastBlockHeight
        }
      }

      return preferredSize
    }
  }

  internal val blocksPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false)).apply {
    add(stubPanelAfterBlock)
  }

  internal val scrollPane = JBScrollPane(
    blocksPanel,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ).apply {
    DataManager.registerDataProvider(this, this@CombinedDiffViewer)
    border = JBUI.Borders.empty()
    viewportBorder = JBUI.Borders.empty()
    viewport.addChangeListener(ViewportChangeListener())
  }

  private val stickyHeaderPanel: Wrapper = Wrapper().apply {
    isOpaque = true
  }

  private val contentPanel: JComponent = object : JBLayeredPane() {
    override fun getPreferredSize(): Dimension = scrollPane.preferredSize

    override fun doLayout() {
      scrollPane.setBounds(0, 0, width, height)
    }
  }.apply {
    isFocusable = false
    add(scrollPane, JLayeredPane.DEFAULT_LAYER, 0)
    add(stickyHeaderPanel, JLayeredPane.POPUP_LAYER, 1)
  }

  private val diffBlocks: MutableMap<CombinedBlockId, CombinedDiffBlock<*>> = linkedMapOf()

  private val diffViewers: MutableMap<CombinedBlockId, DiffViewer> = hashMapOf()

  private val diffBlocksPositions: BidirectionalMap<CombinedBlockId, Int> = BidirectionalMap()

  private val scrollSupport = CombinedDiffScrollSupport(project, this)

  private val focusListener = FocusListener(this)

  private val blockListeners = EventDispatcher.create(BlockListener::class.java)

  private val diffInfo = object : DiffInfo() {
    override fun getContentTitles(): List<String?> {
      return getCurrentBlockId()?.let { blockId -> diffViewers[blockId] as? DiffViewerBase }?.request?.contentTitles ?: return emptyList()
    }
  }

  private val blockNavigation = object : PrevNextDifferenceIterable {
    var currentBlockIndex = 0
      private set

    override fun canGoNext(): Boolean = currentBlockIndex < getDiffBlocksCount() - 1

    override fun canGoPrev(): Boolean = currentBlockIndex > 0

    override fun goNext() {
      currentBlockIndex++
      selectDiffBlock(currentBlockIndex)
    }

    override fun goPrev() {
      currentBlockIndex--
      selectDiffBlock(currentBlockIndex)
    }

    fun setCurrentBlock(index: Int) {
      if (index >= 0 && index < getDiffBlocksCount())
        this.currentBlockIndex = index
    }
  }

  private val combinedEditorSettingsAction =
    CombinedEditorSettingsAction(TextDiffViewerUtil.getTextSettings(context), ::foldingModels, ::editors)

  private val visibleBlocksUpdateQueue =
    MergingUpdateQueue("CombinedDiffViewer.visibleBlocksUpdateQueue", 500, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD)
      .also { Disposer.register(this, it) }

  internal fun updateBlockContent(newContent: CombinedDiffBlockContent) {
    val blockId = newContent.blockId
    val block = getBlockForId(blockId)

    if (block == null) {
      throw IllegalStateException("Block with id $blockId not found in CombinedDiffViewer")
    }

    runPreservingViewportContent {
      val newViewer = newContent.viewer
      diffViewers.remove(blockId)?.also(Disposer::dispose)
      diffViewers[blockId] = newViewer
      removeAdditionalLines(newViewer)
      block.updateBlockContent(newContent)
      newViewer.init()

      diffInfo.update()
      if (blockNavigation.currentBlockIndex == getBlockIndex(blockId)) {
        requestFocusInDiffViewer(newViewer)
      }
    }
  }

  private fun removeAdditionalLines(viewer: DiffViewer) {
    viewer.editors.forEach { editor ->
      editor.settings.additionalLinesCount = 0
      (editor as? EditorImpl)?.resetSizes()
    }
  }

  internal fun addBlock(content: CombinedDiffBlockContent) {
    val blockId = content.blockId
    val diffBlock = createDiffBlock(content)
    val viewer = content.viewer

    blocksPanel.add(diffBlock.component, blocksPanel.componentCount - 1)
    diffBlocks[blockId] = diffBlock
    diffViewers[blockId] = viewer
    diffBlocksPositions[blockId] = getDiffBlocksCount() - 1
    viewer.init()
  }

  private fun createDiffBlock(content: CombinedDiffBlockContent): CombinedDiffBlock<*> {
    val viewer = content.viewer
    if (!viewer.isEditorBased) {
      focusListener.register(viewer.component, this)
    }

    val diffBlockFactory = CombinedDiffBlockFactory.findApplicable<CombinedBlockId>(content)!!

    val diffBlock = diffBlockFactory.createBlock(project, content)
    val blockId = diffBlock.id
    Disposer.register(diffBlock, Disposable {
      diffBlocks.remove(blockId)
      blocksPanel.remove(diffBlock.component)
      diffViewers.remove(blockId)?.also(Disposer::dispose)
      diffBlocksPositions.remove(blockId)
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

    return if (DiffDataKeys.CURRENT_EDITOR.`is`(dataId)) getCurrentDiffViewer()?.editor else null
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
        blockNavigation.goNext()
        currentDiffIterable.goFirst()
      }
    }
  }

  override fun goPrevDiff() {
    when {
      currentDiffIterable.canGoPrev() -> {
        currentDiffIterable.goPrev()
      }
      canGoPrevBlock() -> {
        blockNavigation.goPrev()
        currentDiffIterable.goLast()
      }
    }
  }

  override fun canGoNextBlock(): Boolean = isNavigationEnabled() && blockNavigation.canGoNext()
  override fun canGoPrevBlock(): Boolean = isNavigationEnabled() && blockNavigation.canGoPrev()

  override fun goNextBlock() {
    if (!canGoNextBlock()) return
    blockNavigation.goNext()
    selectDiffBlock(blockNavigation.currentBlockIndex, ScrollPolicy.SCROLL_TO_BLOCK)
    getCurrentDiffViewer()?.editor?.let { EditorActionUtil.moveCaretToTextStart(it, null) }
  }

  override fun goPrevBlock() {
    if (!canGoPrevBlock()) return
    blockNavigation.goPrev()
    selectDiffBlock(blockNavigation.currentBlockIndex, ScrollPolicy.SCROLL_TO_BLOCK)
    getCurrentDiffViewer()?.editor?.let { EditorActionUtil.moveCaretToTextStart(it, null) }
  }

  private fun isNavigationEnabled(): Boolean = diffBlocks.isNotEmpty()

  private fun notifyVisibleBlocksChanged() {
    val delta = CombinedDiffRegistry.getPreloadedBlocksCount()
    val viewRect = scrollPane.viewport.viewRect
    val beforeViewport = arrayOfNulls<CombinedDiffBlock<*>>(delta)
    val afterViewport = arrayOfNulls<CombinedDiffBlock<*>>(delta)
    val blocksInViewport = arrayListOf<CombinedDiffBlock<*>>()
    val hiddenBlocks = arrayListOf<CombinedDiffBlock<*>>()
    var intersectionStarted = false

    for ((index, block) in getAllBlocks().withIndex()) {
      val viewportIntersected = block.component.bounds.intersects(viewRect)

      if (!intersectionStarted && viewportIntersected) {
        intersectionStarted = true
      }

      when {
        !intersectionStarted -> {
          beforeViewport[index.mod(delta)]?.let(hiddenBlocks::add)
          beforeViewport[index.mod(delta)] = block
        }
        viewportIntersected -> blocksInViewport.add(block)
        afterViewport.any(Objects::isNull) -> afterViewport[index.mod(delta)] = block
        else -> hiddenBlocks.add(block)
      }
    }

    if (hiddenBlocks.isNotEmpty()) {
      blockListeners.multicaster.blocksHidden(hiddenBlocks.map(CombinedDiffBlock<*>::id))
    }

    val totalVisible = blocksInViewport + afterViewport.filterNotNull() + beforeViewport.filterNotNull()

    if (totalVisible.isNotEmpty()) {
      blockListeners.multicaster.blocksVisible(totalVisible.map(CombinedDiffBlock<*>::id))
    }
  }

  private fun runPreservingViewportContent(run: () -> Unit) {
    val viewRect = scrollPane.viewport.viewRect

    var anchorBlock: JComponent? = null
    var diff = 0
    var isTopBoundAnchor = false

    for (block in getAllBlocks()) {
      val blockRect = block.component.bounds

      if (blockRect.maxY < viewRect.minY) {
        // full block before the viewport
        continue
      }

      if (blockRect.minY.toInt() == viewRect.minY.toInt()) {
        anchorBlock = block.component
        isTopBoundAnchor = true
        break
      }

      if (blockRect.maxY >= viewRect.minY && blockRect.maxY <= viewRect.maxY) {
        // the bottom of block in the viewport
        anchorBlock = block.component
        diff = (blockRect.maxY - viewRect.minY).roundToInt()
        break
      }

      if (blockRect.maxY > viewRect.maxY && blockRect.minY < viewRect.minY) {
        // this block is larger than the viewport
        anchorBlock = block.component
        isTopBoundAnchor = true
        diff = (blockRect.minY - viewRect.minY).toInt()
        break
      }
    }

    run()

    if (anchorBlock == null) return

    val newViewRect = scrollPane.viewport.viewRect
    val newBlockRect = anchorBlock.bounds

    newViewRect.y = if (isTopBoundAnchor) newBlockRect.minY.toInt() else newBlockRect.maxY.toInt()
    newViewRect.y -= diff
    scrollPane.viewport.viewPosition = Point(newViewRect.x, newViewRect.y)
  }

  private fun updateStickyHeader() {
    val viewRect = scrollPane.viewport.viewRect
    val block = getAllBlocks().find { it.component.bounds.intersects(viewRect) } ?: return
    val stickyHeader = block.stickyHeader

    val headerHeight = block.header.height
    val headerHeightInViewport = min(block.component.bounds.maxY.toInt() - viewRect.bounds.minY.toInt(), headerHeight)
    val stickyHeaderY = headerHeightInViewport - headerHeight

    //scrollPane.verticalScrollBar.add(JBScrollBar.LEADING, stickyHeader)

    stickyHeaderPanel.setContent(stickyHeader)
    stickyHeaderPanel.setBounds(0, stickyHeaderY, block.component.width, headerHeight)
    stickyHeaderPanel.repaint()
  }

  internal fun addBlockListener(listener: BlockListener) {
    blockListeners.listeners.add(listener)
  }

  private fun getBlockId(index: Int) = diffBlocksPositions.getKeysByValue(index)?.singleOrNull()

  private fun getAllBlocks(): Sequence<CombinedDiffBlock<*>> = diffBlocks.values.asSequence()

  private fun getBlockForId(id: CombinedBlockId): CombinedDiffBlock<*>? = diffBlocks[id]
  fun getDiffBlocksCount(): Int = diffBlocks.size

  fun getCurrentBlockId(): CombinedBlockId? {
    return getBlockId(blockNavigation.currentBlockIndex)
  }

  fun getBlockIndex(id: CombinedBlockId): Int? {
    return diffBlocksPositions[id]
  }

  private fun getCurrentDiffViewer(): DiffViewer? = getDiffViewerForIndex(blockNavigation.currentBlockIndex)

  private fun getDiffViewerForIndex(index: Int): DiffViewer? {
    return getBlockId(index)?.let { blockId -> getDiffViewerForId(blockId) }
  }

  internal fun getDiffViewerForId(id: CombinedBlockId): DiffViewer? = diffViewers[id]

  fun selectDiffBlock(blockId: CombinedBlockId?, focusBlock: Boolean, scrollPolicy: ScrollPolicy? = ScrollPolicy.SCROLL_TO_BLOCK) {
    blockId ?: return
    val index = getBlockIndex(blockId)
    if (index == null || index == -1) return

    selectDiffBlock(index, scrollPolicy, focusBlock)
  }

  private fun selectDiffBlock(index: Int,
                              scrollPolicy: ScrollPolicy? = null,
                              focusBlock: Boolean = true) {
    val blockId = getBlockId(index) ?: return
    val block = getBlockForId(blockId) ?: return
    val viewer = getDiffViewerForId(block.id) ?: return

    val doSelect = {
      blockNavigation.setCurrentBlock(index)
      scrollSupport.scroll(index, block, scrollPolicy)
    }

    if (!focusBlock) {
      doSelect()
      return
    }
    requestFocusInDiffViewer(viewer)
    IdeFocusManager.getInstance(project).doWhenFocusSettlesDown(doSelect)
  }

  private fun requestFocusInDiffViewer(newViewer: DiffViewer) {
    val componentToFocus =
      with(newViewer) {
        when {
          isEditorBased -> editor?.contentComponent
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
    if (currentDiffViewer is DiffViewerBase) {
      return currentDiffViewer
    }

    return currentDiffViewer?.let(DiffViewer::getComponent)?.let(DataManager::getDataProvider)
  }

  override fun moveCaretToPrevBlock() {
    blockNavigation.goPrev()
    val editor = getCurrentDiffViewer()?.editor ?: return
    EditorActionUtil.moveCaretToTextEnd(editor, null)
    scrollToCaret()
  }

  override fun moveCaretToNextBlock() {
    blockNavigation.goNext()
    val editor = getCurrentDiffViewer()?.editor ?: return
    EditorActionUtil.moveCaretToTextStart(editor, null)
    scrollToCaret()
  }

  override fun moveCaretPageUp() = movePageUpDown(pageUp = true)

  override fun moveCaretPageDown() = movePageUpDown(pageUp = false)

  private fun movePageUpDown(pageUp: Boolean) {
    // move viewport in the new position
    val viewRect = scrollPane.viewport.viewRect

    val pageHeightWithoutStickyHeader = viewRect.height - stickyHeaderPanel.height
    val editor = getCurrentDiffViewer()?.editor ?: return
    val lineHeight = editor.lineHeight
    val pageOffset = (if (pageUp) -pageHeightWithoutStickyHeader else pageHeightWithoutStickyHeader) / lineHeight * lineHeight

    val maxNewY = scrollPane.viewport.view.height - stubPanelAfterBlock.height - 1
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
      val newEditor = getCurrentDiffViewer()?.editor ?: return
      val pointInNewEditor = SwingUtilities.convertPoint(scrollPane.viewport.view, newPointInView, newEditor.component)
      val visualPositionInNewEditor = newEditor.xyToVisualPosition(pointInNewEditor)
      newEditor.caretModel.moveToVisualPosition(visualPositionInNewEditor)
    }
    scrollToCaret()
  }

  fun scrollToCaret() {
    scrollSupport.combinedEditorsScrollingModel.scrollToCaret(ScrollType.RELATIVE)
  }

  private val editors: List<Editor>
    get() = diffViewers.values.flatMap { it.editors }

  private inner class FocusListener(disposable: Disposable) : FocusAdapter(), FocusChangeListener {

    init {
      (EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx)?.addFocusChangeListener(this, disposable)
    }

    override fun focusGained(editor: Editor) {
      val blockId = diffViewers.entries.find { it.value.editors.contains(editor) }?.key ?: return

      val blockIndex = getBlockIndex(blockId) ?: -1
      if (blockIndex == -1) return

      blockNavigation.setCurrentBlock(blockIndex)
      diffInfo.update()
    }

    override fun focusGained(e: FocusEvent) {
      val blockId = diffViewers.entries.find {
        val diffViewer = it.value
        !diffViewer.isEditorBased && (diffViewer.preferredFocusedComponent == e.component || diffViewer.component == e.component)
      }?.key ?: return

      val blockIndex = getBlockIndex(blockId) ?: -1
      if (blockIndex == -1) return

      blockNavigation.setCurrentBlock(blockIndex)
      diffInfo.update()
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

    fun scroll(index: Int, block: CombinedDiffBlock<*>, scrollPolicy: ScrollPolicy?) {
      val isEditorBased = viewer.getDiffViewerForId(block.id)?.isEditorBased ?: false
      if (scrollPolicy == ScrollPolicy.SCROLL_TO_BLOCK || !isEditorBased) {
        scrollToDiffBlock(index)
      }
      else if (scrollPolicy == ScrollPolicy.SCROLL_TO_CARET) {
        scrollToDiffChangeWithCaret()
      }
    }

    private fun scrollToDiffChangeWithCaret() {
      if (viewer.getCurrentDiffViewer().isEditorBased) { //avoid scrolling for non editor based viewers
        combinedEditorsScrollingModel.scrollToCaret(ScrollType.CENTER)
      }
    }

    private fun scrollToDiffBlock(index: Int) {
      if (viewer.getDiffViewerForIndex(index) != null) {
        val bounds = viewer.blocksPanel.components.getOrNull(index)?.bounds ?: return
        bounds.height = Int.MAX_VALUE
        viewer.blocksPanel.scrollRectToVisible(bounds)
      }
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

      fun goFirst() {
        val diffIterable = viewer.getDifferencesIterable() ?: return
        while (diffIterable.canGoPrev()) diffIterable.goPrev()
        scrollToDiffChangeWithCaret()
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

      override fun getEditor(): Editor = viewer.getCurrentDiffViewer()?.editor ?: dummyEditor

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

private val DiffViewer.editor: EditorEx?
  get() = when (this) {
    is OnesideTextDiffViewer -> editor
    is TwosideTextDiffViewer -> currentEditor
    is ThreesideTextDiffViewer -> currentEditor
    is UnifiedDiffViewer -> editor
    else -> null
  }

val DiffViewer.editors: List<EditorEx>
  get() = when (this) {
    is OnesideTextDiffViewer -> editors
    is TwosideTextDiffViewer -> editors
    is ThreesideTextDiffViewer -> editors
    is UnifiedDiffViewer -> listOf(editor)
    else -> emptyList()
  }

private val DiffViewer?.isEditorBased: Boolean
  get() = this is DiffViewerBase &&
          this !is OnesideBinaryDiffViewer &&  //TODO simplify, introduce ability to distinguish editor and non-editor based DiffViewer
          this !is ThreesideBinaryDiffViewer &&
          this !is TwosideBinaryDiffViewer

internal interface BlockListener : EventListener {
  fun blocksHidden(blockIds: Collection<CombinedBlockId>)
  fun blocksVisible(blockIds: Collection<CombinedBlockId>)
}
