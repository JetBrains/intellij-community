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
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
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
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.min
import kotlin.math.roundToInt

class CombinedDiffViewer(private val context: DiffContext) : DiffViewer, DataProvider {
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

  internal val scrollSupport = CombinedDiffScrollSupport(project, this)

  private val focusListener = FocusListener(this)

  private val blockListeners = EventDispatcher.create(BlockListener::class.java)

  private val diffInfo = object : DiffInfo() {
    override fun getContentTitles(): List<String?> {
      return getCurrentBlockId()?.let { blockId -> diffViewers[blockId] as? DiffViewerBase }?.request?.contentTitles ?: return emptyList()
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
      block.updateBlockContent(newContent)
      newViewer.init()
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

  override fun getData(dataId: @NonNls String): Any? {
    if (CommonDataKeys.PROJECT.`is`(dataId)) return project
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.`is`(dataId)) return scrollSupport.currentPrevNextIterable
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

  fun isNavigationEnabled(): Boolean = diffBlocks.isNotEmpty()

  fun hasNextChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = scrollSupport.blockIterable.index
    return curFilesIndex != -1 && curFilesIndex < getDiffBlocksCount() - 1
  }

  fun hasPrevChange(fromUpdate: Boolean): Boolean {
    val curFilesIndex = scrollSupport.blockIterable.index
    return curFilesIndex != -1 && curFilesIndex > 0
  }

  fun goToNextChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, true)
  }

  fun goToPrevChange(fromDifferences: Boolean) {
    goToChange(fromDifferences, false)
  }

  private fun goToChange(fromDifferences: Boolean, next: Boolean) {
    val differencesIterable = getDifferencesIterable()
    val blocksIterable = getBlocksIterable()
    val canGoToDifference = { if (next) differencesIterable?.canGoNext() == true else differencesIterable?.canGoPrev() == true }
    val goToDifference = { if (next) differencesIterable?.goNext() else differencesIterable?.goPrev() }
    val canGoToBlock = { if (next) blocksIterable.canGoNext() else blocksIterable.canGoPrev() }
    val goToBlock = { if (next) blocksIterable.goNext() else blocksIterable.goPrev() }

    when {
      fromDifferences && canGoToDifference() -> goToDifference()
      fromDifferences && canGoToBlock() -> {
        goToBlock()
        selectDiffBlock(ScrollPolicy.DIFF_CHANGE)
      }

      canGoToBlock() -> {
        goToBlock()
        selectDiffBlock(ScrollPolicy.DIFF_BLOCK)
      }
    }
  }

  internal enum class IterationState {
    NEXT, PREV, NONE
  }

  internal var iterationState = IterationState.NONE

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
    return getBlockId(scrollSupport.blockIterable.index)
  }

  fun getBlockIndex(id: CombinedBlockId): Int? {
    return diffBlocksPositions[id]
  }

  internal fun getDifferencesIterable(): PrevNextDifferenceIterable? {
    return getCurrentDataProvider()?.let(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE::getData)
  }

  private fun getBlocksIterable(): PrevNextDifferenceIterable = scrollSupport.blockIterable

  internal fun getCurrentDiffViewer(): DiffViewer? = getDiffViewerForIndex(scrollSupport.blockIterable.index)

  internal fun getDiffViewerForIndex(index: Int): DiffViewer? {
    return getBlockId(index)?.let { blockId -> getDiffViewerForId(blockId) }
  }

  internal fun getDiffViewerForId(id: CombinedBlockId): DiffViewer? = diffViewers[id]

  fun selectDiffBlock(blockId: CombinedBlockId?, focusBlock: Boolean, onSelected: () -> Unit = {}) {
    blockId ?: return
    val index = getBlockIndex(blockId)
    if (index == null || index == -1) return

    selectDiffBlock(index, ScrollPolicy.DIFF_BLOCK, focusBlock, onSelected)
  }

  private fun selectDiffBlock(scrollPolicy: ScrollPolicy) {
    selectDiffBlock(scrollSupport.blockIterable.index, scrollPolicy)
  }

  private fun selectDiffBlock(index: Int = scrollSupport.blockIterable.index,
                              scrollPolicy: ScrollPolicy,
                              focusBlock: Boolean = true,
                              onSelected: () -> Unit = {}) {
    val blockId = getBlockId(index) ?: return
    val block = getBlockForId(blockId) ?: return

    selectDiffBlock(index, block, scrollPolicy, focusBlock, onSelected)
  }

  private fun selectDiffBlock(index: Int,
                              block: CombinedDiffBlock<*>,
                              scrollPolicy: ScrollPolicy,
                              focusBlock: Boolean,
                              onSelected: () -> Unit) {
    val viewer = getDiffViewerForId(block.id) ?: return

    val doSelect = {
      onSelected()
      scrollSupport.blockIterable.index = index
      scrollSupport.scroll(index, block, scrollPolicy)
    }

    if (!focusBlock) {
      doSelect()
      return
    }

    val componentToFocus =
      with(viewer) {
        when {
          isEditorBased -> editor?.contentComponent
          preferredFocusedComponent != null -> preferredFocusedComponent
          else -> component
        }
      } ?: return
    val focusManager = IdeFocusManager.getInstance(project)
    if (focusManager.focusOwner == componentToFocus) return

    focusManager.requestFocus(componentToFocus, true)
    focusManager.doWhenFocusSettlesDown(doSelect)
  }

  private fun createToolbarActions(): List<AnAction> {
    return listOf(combinedEditorSettingsAction)
  }

  internal fun contentChanged() {
    combinedEditorSettingsAction.installGutterPopup()
    combinedEditorSettingsAction.applyDefaults()
    editors.forEach { editor ->
      //editor.settings.additionalLinesCount = 5
      //(editor as? EditorEx)?.setVerticalScrollbarVisible(false)
    }
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

  private val editors: List<Editor>
    get() = diffViewers.values.flatMap { it.editors }

  private inner class FocusListener(disposable: Disposable) : FocusAdapter(), FocusChangeListener {

    init {
      (EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx)?.addFocusChangeListener(this, disposable)
    }

    override fun focusGained(editor: Editor) {
      val indexOfSelectedBlock =
        diffViewers.entries.find { editor == it.value.editor }?.key?.let { blockId -> getBlockIndex(blockId) } ?: -1
      if (indexOfSelectedBlock != -1) {
        scrollSupport.blockIterable.index = indexOfSelectedBlock
        diffInfo.update()
      }
    }

    override fun focusGained(e: FocusEvent) {
      val indexOfSelectedBlock =
        diffViewers.entries.find {
          val v = it.value
          !v.isEditorBased && (v.preferredFocusedComponent == e.component || v.component == e.component)
        }?.key?.let { blockId -> getBlockIndex(blockId) } ?: -1

      if (indexOfSelectedBlock != -1) {
        scrollSupport.blockIterable.index = indexOfSelectedBlock
        diffInfo.update()
      }
    }

    fun register(component: JComponent, disposable: Disposable) {
      ListenerUtil.addFocusListener(component, this)
      Disposer.register(disposable) { ListenerUtil.removeFocusListener(component, this) }
    }
  }
}

val DiffViewer.editor: EditorEx?
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

internal val DiffViewer?.isEditorBased: Boolean
  get() = this is DiffViewerBase &&
          this !is OnesideBinaryDiffViewer &&  //TODO simplify, introduce ability to distinguish editor and non-editor based DiffViewer
          this !is ThreesideBinaryDiffViewer &&
          this !is TwosideBinaryDiffViewer

internal interface BlockListener : EventListener {
  fun blocksHidden(blockIds: Collection<CombinedBlockId>)
  fun blocksVisible(blockIds: Collection<CombinedBlockId>)
}
