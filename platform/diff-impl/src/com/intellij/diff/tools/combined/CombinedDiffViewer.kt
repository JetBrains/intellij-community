// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ListenerUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.annotations.NonNls
import java.awt.Rectangle
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max

class CombinedDiffViewer(context: DiffContext, val unifiedDiff: Boolean) : DiffViewer, DataProvider {
  private val project = context.project

  internal val contentPanel = JPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, false))
  internal val scrollPane = JBScrollPane(
    contentPanel,
    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
  ).apply {
    DataManager.registerDataProvider(this, this@CombinedDiffViewer)
    border = JBUI.Borders.empty()
    viewportBorder = JBUI.Borders.empty()
    viewport.addChangeListener(ViewportChangeListener())
  }

  internal val diffBlocks = linkedMapOf<CombinedBlockId, CombinedDiffBlock<*>>()
  internal val diffViewers = hashMapOf<CombinedBlockId, DiffViewer>()
  internal val diffBlocksPositions = BidirectionalMap<CombinedBlockId, Int>()

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

  private var blockToSelect: CombinedDiffBlock<*>? = null

  private val visibleBlocksUpdateQueue =
    MergingUpdateQueue("CombinedDiffViewer.visibleBlocksUpdateQueue", 500, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD)
      .also { Disposer.register(this, it) }

  internal fun updateBlockContent(block: CombinedDiffBlock<*>, newContent: CombinedDiffBlockContent) {
    val newViewer = newContent.viewer
    diffViewers.remove(block.id)?.also(Disposer::dispose)
    diffViewers[block.id] = newViewer
    block.updateBlockContent(newViewer.component)
    newViewer.init()
  }

  internal fun addChildBlock(content: CombinedDiffBlockContent, needBorder: Boolean) {
    val diffBlock = createDiffBlock(content, needBorder)
    val viewer = content.viewer

    contentPanel.add(diffBlock.component)
    diffBlocks[diffBlock.id] = diffBlock
    diffViewers[diffBlock.id] = viewer
    diffBlocksPositions[diffBlock.id] = diffBlocks.size - 1
    viewer.init()
  }

  internal fun insertChildBlock(content: CombinedDiffBlockContent, position: CombinedDiffRequest.InsertPosition?): CombinedDiffBlock<*> {
    val above = position?.above ?: false
    val insertIndex =
      if (position == null) -1
      else diffBlocksPositions[position.blockId]?.let { if (above) it else it.inc() } ?: -1

    val diffBlock = createDiffBlock(content, diffBlocks.size > 1 && insertIndex > 0)
    val blockId = diffBlock.id
    val viewer = content.viewer

    if (insertIndex != -1 && insertIndex < diffBlocks.size) {
      contentPanel.add(diffBlock.component, insertIndex)
      diffBlocks[blockId] = diffBlock
      diffViewers[blockId] = viewer
      diffBlocksPositions[blockId] = insertIndex
    }
    else {
      contentPanel.add(diffBlock.component)
      diffBlocks[blockId] = diffBlock
      diffViewers[blockId] = viewer
      diffBlocksPositions[blockId] = contentPanel.componentCount - 1
    }

    viewer.init()

    return diffBlock
  }

  private fun createDiffBlock(content: CombinedDiffBlockContent, needBorder: Boolean): CombinedDiffBlock<*> {
    val viewer = content.viewer
    if (!viewer.isEditorBased) {
      focusListener.register(viewer.component, this)
    }

    val diffBlockFactory = CombinedDiffBlockFactory.findApplicable<CombinedBlockId>(content)!!

    val diffBlock = diffBlockFactory.createBlock(content, needBorder)
    val blockId = diffBlock.id
    Disposer.register(diffBlock, Disposable {
      diffBlocks.remove(blockId)
      contentPanel.remove(diffBlock.component)
      diffViewers.remove(blockId)?.also(Disposer::dispose)
      diffBlocksPositions.remove(blockId)
    })
    Disposer.register(this, diffBlock)

    return diffBlock
  }

  override fun getComponent(): JComponent = scrollPane

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

  private inner class ViewportChangeListener: ChangeListener {

    override fun stateChanged(e: ChangeEvent) {
      visibleBlocksUpdateQueue.queue(object : Update(e) {
        override fun run() = notifyVisibleBlocksChanged()
        override fun canEat(update: Update?): Boolean = true
      })
    }
  }

  private fun notifyVisibleBlocksChanged() {
    val viewRect = scrollPane.viewport.viewRect
    val (visibleBlocks, hiddenBlocks) = getAllBlocks().partition { it.component.bounds.intersects(viewRect) }

    if (visibleBlocks.isNotEmpty()) {
      blockListeners.multicaster.blocksHidden(hiddenBlocks)
    }

    if (visibleBlocks.isNotEmpty()) {
      updateGlobalBlockHeader(visibleBlocks, viewRect)
      blockListeners.multicaster.blocksVisible(visibleBlocks, blockToSelect)
    }
  }

  private fun updateGlobalBlockHeader(visibleBlocks: List<CombinedDiffBlock<*>>, viewRect: Rectangle) {
    val firstVisibleBlock = visibleBlocks.first()
    val blockOnTop = firstVisibleBlock.component.bounds.y == viewRect.y
    val previousBlockPosition = max((diffBlocksPositions[firstVisibleBlock.id] ?: -1) - 1, 0)
    val firstBlock = diffBlocks.values.first()
    val firstBlockComponent = firstBlock.component
    val firstBlockHeader = firstBlock.header
    val previousBlockHeader = (getBlockId(previousBlockPosition)?.let { diffBlocks[it] } as? CombinedDiffGlobalBlockHeaderProvider)?.globalHeader
    val firstVisibleBlockHeader = (firstVisibleBlock as? CombinedDiffGlobalBlockHeaderProvider)?.globalHeader

    when {
      blockOnTop -> scrollPane.setColumnHeaderView(previousBlockHeader)
      firstBlockComponent.bounds.y == viewRect.y -> scrollPane.setColumnHeaderView(firstBlockHeader)
      else -> scrollPane.setColumnHeaderView(firstVisibleBlockHeader)
    }
  }

  internal fun addBlockListener(listener: BlockListener) {
    blockListeners.listeners.add(listener)
  }

  private fun getBlockId(index: Int) = diffBlocksPositions.getKeysByValue(index)?.singleOrNull()

  fun getAllBlocks() = diffBlocks.values.asSequence()

  fun getBlock(viewer: DiffViewer) = diffViewers.entries.find { it == viewer }?.key?.let { blockId -> diffBlocks[blockId] }

  fun getViewer(id: CombinedBlockId) = diffViewers[id]

  fun getCurrentBlockId(): CombinedBlockId? {
    return getBlockId(scrollSupport.blockIterable.index)
  }

  internal fun getDifferencesIterable(): PrevNextDifferenceIterable? {
    return getCurrentDataProvider()?.let(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE::getData)
  }

  internal fun getBlocksIterable(): PrevNextDifferenceIterable = scrollSupport.blockIterable

  internal fun getCurrentDiffViewer(): DiffViewer? = getDiffViewer(scrollSupport.blockIterable.index)

  internal fun getDiffViewer(index: Int): DiffViewer? {
    return getBlockId(index)?.let { blockId -> diffViewers[blockId] }
  }

  fun selectDiffBlock(filePath: FilePath, fileStatus: FileStatus, scrollPolicy: ScrollPolicy, onSelected: () -> Unit = {}) {
    val blockId = CombinedPathBlockId(filePath, fileStatus)
    val index = diffBlocksPositions[blockId]
    if (index == null || index == -1) return

    selectDiffBlock(index, scrollPolicy, onSelected)
  }

  internal fun selectDiffBlock(scrollPolicy: ScrollPolicy) {
    selectDiffBlock(scrollSupport.blockIterable.index, scrollPolicy)
  }

  fun selectDiffBlock(index: Int = scrollSupport.blockIterable.index, scrollPolicy: ScrollPolicy, onSelected: () -> Unit = {}) {
    getBlockId(index)?.let { diffBlocks[it] }?.run {
      selectDiffBlock(index, this, scrollPolicy, onSelected)
    }
  }

  fun selectDiffBlock(block: CombinedDiffBlock<*>, scrollPolicy: ScrollPolicy, onSelected: () -> Unit = {}) {
    val index = diffBlocksPositions[block.id]
    if (index == null || index == -1) return

    selectDiffBlock(index, block, scrollPolicy, onSelected)
  }

  private fun selectDiffBlock(index: Int, block: CombinedDiffBlock<*>, scrollPolicy: ScrollPolicy, onSelected: () -> Unit) {
    val viewer = diffViewers[block.id] ?: return

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
    focusManager.doWhenFocusSettlesDown {
      onSelected()
      blockToSelect = block
      scrollSupport.scroll(index, block, scrollPolicy)
    }
  }

  private fun createToolbarActions(): List<AnAction> {
    return listOf(combinedEditorSettingsAction)
  }

  internal fun contentChanged() {
    blockToSelect = null
    combinedEditorSettingsAction.installGutterPopup()
    combinedEditorSettingsAction.applyDefaults()
    editors.forEach { editor -> editor.settings.additionalLinesCount = 0 }
  }

  private val foldingModels: List<FoldingModelSupport>
    get() = diffViewers.mapNotNull { viewer ->
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
        diffViewers.entries.find { editor == it.value.editor }?.key?.let { blockId -> diffBlocksPositions[blockId] } ?: -1
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
        }?.key?.let { blockId -> diffBlocksPositions[blockId] } ?: -1

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
    is UnifiedDiffViewer -> editor
    else -> null
  }

val DiffViewer.editors: List<EditorEx>
  get() = when (this) {
    is OnesideTextDiffViewer -> editors
    is TwosideTextDiffViewer -> editors
    is UnifiedDiffViewer -> listOf(editor)
    else -> emptyList()
  }

internal val DiffViewer?.isEditorBased: Boolean
  get() = this is DiffViewerBase &&
          this !is OnesideBinaryDiffViewer &&  //TODO simplify, introduce ability to distinguish editor and non-editor based DiffViewer
          this !is ThreesideBinaryDiffViewer &&
          this !is TwosideBinaryDiffViewer

internal interface BlockListener : EventListener {
  fun blocksHidden(blocks: Collection<CombinedDiffBlock<*>>)
  fun blocksVisible(blocks: Collection<CombinedDiffBlock<*>>, blockToSelect: CombinedDiffBlock<*>?)
}
