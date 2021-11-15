// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.ListenerUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.NonNls
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

var COMBINED_DIFF_VIEWER = DataKey.create<CombinedDiffViewer>("combined_diff_viewer")

class CombinedDiffViewer(private val context: DiffContext, val unifiedDiff: Boolean) : FrameDiffTool.DiffViewer, DataProvider {
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
  }

  internal val diffBlocks = mutableListOf<CombinedDiffBlock>()

  internal val scrollSupport = CombinedDiffScrollSupport(project, this)

  private val focusListener = FocusListener(this)

  private val diffInfo = object : DiffInfo() {
    override fun getContentTitles(): List<String?> {
      return (getCurrentBlockContent()?.viewer as? DiffViewerBase)?.request?.contentTitles ?: return emptyList()
    }
  }

  internal fun addChildBlock(content: CombinedDiffBlockContent, needBorder: Boolean) {
    val diffBlockFactory = CombinedDiffBlockFactory.findApplicable(content) ?: return

    val viewer = content.viewer
    if (viewer.isEditorBased) {
      viewer.editors.forEach { it.settings.additionalLinesCount = 0 }
    }
    else {
      focusListener.register(viewer.component, this)
    }

    val diffBlock = diffBlockFactory.createBlock(content, needBorder)
    Disposer.register(this, diffBlock)

    contentPanel.add(diffBlock.component)
    diffBlocks.add(diffBlock)
    viewer.init()
  }

  override fun getComponent(): JComponent = scrollPane

  override fun getPreferredFocusedComponent(): JComponent? = getCurrentDiffViewer()?.preferredFocusedComponent

  override fun init(): FrameDiffTool.ToolbarComponents {
    val components = FrameDiffTool.ToolbarComponents()
    components.toolbarActions = createToolbarActions()
    components.diffInfo = diffInfo
    return components
  }

  override fun dispose() {}

  override fun getData(dataId: @NonNls String): Any? {
    if (CommonDataKeys.PROJECT.`is`(dataId)) return project
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.`is`(dataId)) return scrollSupport.currentPrevNextIterable
    if (DiffDataKeys.NAVIGATABLE.`is`(dataId)) return getCurrentDataProvider()?.let(DiffDataKeys.NAVIGATABLE::getData)
    if (DiffDataKeys.DIFF_VIEWER.`is`(dataId)) return getCurrentDiffViewer()
    if (COMBINED_DIFF_VIEWER.`is`(dataId)) return this

    return if (DiffDataKeys.CURRENT_EDITOR.`is`(dataId)) getCurrentDiffViewer()?.editor else null
  }

  fun getCurrentBlockContent(): CombinedDiffBlockContent? {
    return diffBlocks.getOrNull(scrollSupport.blockIterable.index)?.content
  }

  internal fun getDifferencesIterable(): PrevNextDifferenceIterable? {
    return getCurrentDataProvider()?.let(DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE::getData)
  }

  internal fun getBlocksIterable(): PrevNextDifferenceIterable = scrollSupport.blockIterable

  internal fun getCurrentDiffViewer(): DiffViewer? = getDiffViewer(scrollSupport.blockIterable.index)

  internal fun getDiffViewer(index: Int): DiffViewer? {
    return diffBlocks.getOrNull(index)?.content?.viewer
  }

  fun selectDiffBlock(filePath: FilePath, fileStatus: FileStatus, scrollPolicy: ScrollPolicy, onSelected: () -> Unit = {}) {
    val index = diffBlocks.indexOfFirst { block -> block.content.path == filePath && block.content.fileStatus == fileStatus }
    if (index == -1) return

    selectDiffBlock(index, scrollPolicy, onSelected)
  }

  internal fun selectDiffBlock(scrollPolicy: ScrollPolicy) {
    selectDiffBlock(scrollSupport.blockIterable.index, scrollPolicy)
  }

  internal fun selectDiffBlock(index: Int = scrollSupport.blockIterable.index, scrollPolicy: ScrollPolicy, onSelected: () -> Unit = {}) {
    diffBlocks.getOrNull(index)?.run {
      selectDiffBlock(index, this, scrollPolicy, onSelected)
    }
  }

  private fun selectDiffBlock(index: Int, block: CombinedDiffBlock, scrollPolicy: ScrollPolicy, onSelected: () -> Unit) {
    val componentToFocus = with(block.content.viewer)
                           { if (isEditorBased) editor?.contentComponent else preferredFocusedComponent ?: component }
                           ?: return
    val focusManager = IdeFocusManager.getInstance(project)
    if (focusManager.focusOwner == componentToFocus) return

    focusManager.requestFocus(componentToFocus, true)
    focusManager.doWhenFocusSettlesDown {
      onSelected()
      scrollSupport.scroll(index, block, scrollPolicy)
    }
  }

  private fun createToolbarActions(): List<AnAction> {
    val textSettings = TextDiffViewerUtil.getTextSettings(context)

    return listOf(CombinedEditorSettingsAction(textSettings, getFoldingModels(), editors).apply { applyDefaults() })
  }

  private fun getFoldingModels(): List<FoldingModelSupport> {
    return diffBlocks.mapNotNull { block ->
      with(block.content.viewer) {
        when (this) {
          is SimpleDiffViewer -> foldingModel
          is UnifiedDiffViewer -> foldingModel
          else -> null
        }
      }
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
    get() = diffBlocks.flatMap { it.content.viewer.editors }

  private inner class FocusListener(disposable: Disposable) : FocusAdapter(), FocusChangeListener {

    init {
      (EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx)?.addFocusChangeListener(this, disposable)
    }

    override fun focusGained(editor: Editor) {
      val indexOfSelectedBlock = diffBlocks.indexOfFirst { b -> editor == b.content.viewer.editor }
      if (indexOfSelectedBlock != -1) {
        scrollSupport.blockIterable.index = indexOfSelectedBlock
        diffInfo.update()
      }
    }

    override fun focusGained(e: FocusEvent) {
      val indexOfSelectedBlock =
        diffBlocks.indexOfFirst {
          val v = it.content.viewer
          !v.isEditorBased && (v.preferredFocusedComponent == e.component || v.component == e.component)
        }

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

internal val FrameDiffTool.DiffViewer.editor: Editor?
  get() = when (this) {
    is OnesideTextDiffViewer -> editor
    is TwosideTextDiffViewer -> currentEditor
    is UnifiedDiffViewer -> editor
    else -> null
  }

internal val FrameDiffTool.DiffViewer.editors: List<Editor>
  get() = when (this) {
    is OnesideTextDiffViewer -> editors
    is TwosideTextDiffViewer -> editors
    is UnifiedDiffViewer -> listOf(editor)
    else -> emptyList()
  }

internal val FrameDiffTool.DiffViewer?.isEditorBased: Boolean
  get() = this is DiffViewerBase &&
          this !is OnesideBinaryDiffViewer &&  //TODO simplify, introduce ability to distinguish editor and non-editor based DiffViewer
          this !is ThreesideBinaryDiffViewer &&
          this !is TwosideBinaryDiffViewer
