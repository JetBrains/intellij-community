// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.diff.impl.DiffUsageTriggerCollector
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.ClientProperty
import com.intellij.ui.ListenerUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.EventDispatcher
import com.intellij.util.FontUtil
import com.intellij.util.IconUtil.getIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.properties.Delegates

@ApiStatus.Experimental
class CombinedBlockProducer(
  val id: CombinedBlockId,
  val producer: DiffRequestProducer
)

@ApiStatus.Experimental
interface CombinedBlockId

@ApiStatus.Experimental
interface CombinedDiffBlock<ID : CombinedBlockId> : Disposable {
  val id: ID

  val header: JComponent
  val stickyHeaderComponent: JComponent
  val preferredFocusComponent: JComponent

  val body: JComponent
  val component: JComponent

  fun updateBlockContent(newContent: CombinedDiffBlockContent) {}
}

@ApiStatus.Experimental
interface CombinedSelectableDiffBlock<ID : CombinedBlockId> : CombinedDiffBlock<ID> {
  fun setSelected(selected: Boolean)
  fun updateBorder(updateStickyHeaderBottomBorder: Boolean) {}
}

@ApiStatus.Experimental
interface CombinedCollapsibleDiffBlock<ID : CombinedBlockId> : CombinedSelectableDiffBlock<ID> {
  fun setCollapsed(collapsed: Boolean)

  fun addListener(listener: CombinedDiffBlockListener, parentDisposable: Disposable)
}

@ApiStatus.Experimental
interface CombinedDiffBlockListener : EventListener {
  fun onCollapseStateChanged(id: CombinedBlockId, collapseState: Boolean)
}

@ApiStatus.Experimental
class CombinedDiffBlockContent(val viewer: FrameDiffTool.DiffViewer, val blockId: CombinedBlockId)

@ApiStatus.Experimental
interface CombinedDiffBlockFactory<ID : CombinedBlockId> {
  fun createBlock(project: Project, content: CombinedDiffBlockContent, isCollapsed: Boolean): CombinedDiffBlock<ID>
}

internal class CombinedSimpleDiffBlockFactory : CombinedDiffBlockFactory<CombinedPathBlockId> {
  override fun createBlock(project: Project,
                           content: CombinedDiffBlockContent,
                           isCollapsed: Boolean): CombinedCollapsibleDiffBlock<CombinedPathBlockId> =
    with(content.blockId as CombinedPathBlockId) {
      CombinedSimpleDiffBlock(project, this, content.viewer.component, content.viewer is CombinedDiffLoadingBlock, isCollapsed)
    }
}

private class CombinedSimpleDiffHeader(project: Project,
                                       blockId: CombinedPathBlockId,
                                       withPathOnly: Boolean,
                                       onClick: () -> Unit) :
  CombinedDiffSelectablePanel(regularBackground = CombinedDiffUI.BLOCK_HEADER_BACKGROUND, onClick = onClick) {

  private var toolbar: ActionToolbar? = null

  init {
    addToCenter(if (withPathOnly) createTextComponent(project, blockId.path) else buildToolbar(project, blockId).component)
    border = JBUI.Borders.empty(CombinedDiffUI.BLOCK_HEADER_INSETS)
  }

  private fun buildToolbar(project: Project, blockId: CombinedPathBlockId): ActionToolbar {
    val path = blockId.path
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(OpenInEditorAction())
    toolbarGroup.addSeparator()
    toolbarGroup.add(SelectableFilePathLabel(project, path))

    val toolbar = ActionManager.getInstance().createActionToolbar("CombinedDiffBlockHeaderToolbar", toolbarGroup, true)
    toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
    toolbar.component.isOpaque = false
    toolbar.component.border = JBUI.Borders.empty()
    this.toolbar = toolbar

    return toolbar
  }

  fun setToolbarTargetComponent(component: JComponent, componentCanBeHidden: Boolean) {
    ClientProperty.put(component, ActionUtil.ALLOW_ACTION_PERFORM_WHEN_HIDDEN, componentCanBeHidden)
    toolbar?.setTargetComponent(component)
  }

  override fun getSelectionBackground(state: State): Color = CombinedDiffUI.BLOCK_HEADER_BACKGROUND
  override fun changeBackgroundOnHover(state: State): Boolean = true

  private class SelectableFilePathLabel(private val project: Project,
                                        private val path: FilePath) : DumbAwareAction(), CustomComponentAction {

    private val checkBox = CheckBox("").apply { background = UIUtil.getListBackground() }

    var selected: Boolean
      get() = checkBox.isSelected
      set(value) {
        checkBox.isSelected = value
      }

    fun setSelectable(selectable: Boolean) {
      checkBox.isVisible = selectable
    }

    init {
      selected = false
      setSelectable(false)
    }

    override fun actionPerformed(e: AnActionEvent) {}

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      val textComponent = createTextComponent(project, path)
      val component = NonOpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(3), 0))
        .apply {
          add(checkBox)
          add(textComponent)
        }

      return component
    }
  }

  companion object {
    private fun createTextComponent(project: Project, path: FilePath): JComponent {
      val simpleColoredComponent = SimpleColoredComponent()
      simpleColoredComponent.isOpaque = false
      simpleColoredComponent.append(path.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
      simpleColoredComponent.apply {
        val parentPath = path.parentPath?.let(FilePath::getPresentableUrl)?.let(FileUtil::getLocationRelativeToUserHome)
        if (parentPath != null) {
          append(FontUtil.spaceAndThinSpace() + parentPath, SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
        icon = getIcon(project, path)
      }
      return simpleColoredComponent
    }

    private fun getIcon(project: Project?, path: FilePath): Icon? {
      if (project != null && project.isDisposed) return null
      val file = path.virtualFile
      if (file != null) return getIcon(file, 0, project)

      return FileTypeManager.getInstance().getFileTypeByFileName(path.name).icon
    }
  }
}

@ApiStatus.Experimental
data class CombinedPathBlockId(val path: FilePath, val fileStatus: FileStatus?, val tag: Any? = null) : CombinedBlockId

internal class CombinedSimpleDiffBlock(project: Project,
                                       override val id: CombinedPathBlockId,
                                       private var content: JComponent,
                                       isPathOnlyHeader: Boolean,
                                       isCollapsed: Boolean = false
) : CombinedDiffBlock<CombinedPathBlockId>,
    CombinedCollapsibleDiffBlock<CombinedPathBlockId>,
    CombinedSelectableDiffBlock<CombinedPathBlockId> {

  private val collapsingListeners = EventDispatcher.create(CombinedDiffBlockListener::class.java)

  private val pathOnlyHeader: CombinedSimpleDiffHeader = CombinedSimpleDiffHeader(project, id, true, ::toggleCollapse)
  private val headerWithToolbar: CombinedSimpleDiffHeader = CombinedSimpleDiffHeader(project, id, false, ::toggleCollapse)
  private val stickyHeader: CombinedSimpleDiffHeader = CombinedSimpleDiffHeader(project, id, false, ::toggleCollapse).apply {
    background = CombinedDiffUI.BLOCK_HEADER_BACKGROUND
  }

  override val header: Wrapper = Wrapper(if (isPathOnlyHeader) pathOnlyHeader else headerWithToolbar)
  override val preferredFocusComponent: JComponent get() = header.targetComponent

  override val stickyHeaderComponent: CombinedDiffContainerPanel = CombinedDiffContainerPanel(BorderLayout(0, 0), false)
    .apply {
      background = UIUtil.getPanelBackground()
      add(stickyHeader, BorderLayout.CENTER)
    }

  override val body: Wrapper = if (isCollapsed) Wrapper() else Wrapper(content)

  override val component: CombinedDiffContainerPanel = object : CombinedDiffContainerPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true), true) {
    init {
      background = UIUtil.getPanelBackground()
    }

    override fun getPreferredSize(): Dimension {
      val preferredSize = super.getPreferredSize()
      if (body.isNull || !SystemInfo.isMac) return preferredSize

      // When a horizontal toolbar is displayed in an editor, it eats up part of the viewport's height,
      // resulting in vertical scrollbars appearing
      // this hack allows to provide additional space for horizontal scrollbar
      // FIXME: should be fixed properly on the level of editor
      return Dimension(preferredSize.width, preferredSize.height + JBUI.scale(14))
    }
  }

  private var blockCollapsed by Delegates.observable(isCollapsed) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      DiffUsageTriggerCollector.logToggleCombinedDiffBlockCollapse(project)
      updateBodyContent(newValue)
      collapsingListeners.multicaster.onCollapseStateChanged(id, newValue)
    }
  }

  private var blockSelected by Delegates.observable(false) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      pathOnlyHeader.selected = newValue
      headerWithToolbar.selected = newValue
      stickyHeader.selected = newValue
      updateBorder(updateStickyHeaderBottomBorder = stickyHeaderComponent.roundedBottom)
    }
  }

  private fun setFocused(state: Boolean) {
    pathOnlyHeader.focused = state
    headerWithToolbar.focused = state
    stickyHeader.focused = state
    updateBorder(updateStickyHeaderBottomBorder = stickyHeaderComponent.roundedBottom)
  }

  override fun updateBorder(updateStickyHeaderBottomBorder: Boolean) {
    val isFocused = pathOnlyHeader.focused || headerWithToolbar.focused || stickyHeader.focused
    val borderColor = CombinedDiffUI.getBlockBorderColor(blockSelected, isFocused)
    stickyHeaderComponent.borderColor = borderColor
    stickyHeaderComponent.bottomBorderColor = if (updateStickyHeaderBottomBorder) borderColor else CombinedDiffUI.EDITOR_BORDER_COLOR
    stickyHeaderComponent.roundedBottom = updateStickyHeaderBottomBorder
    component.borderColor = borderColor
  }

  override fun setSelected(selected: Boolean) {
    blockSelected = selected
  }

  override fun setCollapsed(collapsed: Boolean) {
    blockCollapsed = collapsed
  }

  override fun addListener(listener: CombinedDiffBlockListener, parentDisposable: Disposable) {
    collapsingListeners.addListener(listener, parentDisposable)
  }

  private fun setHeaderToolbarTargetComponent(component: JComponent) {
    headerWithToolbar.setToolbarTargetComponent(component, true)
    stickyHeader.setToolbarTargetComponent(component, true)
  }

  private val focusListener = object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      setFocused(true)
    }

    override fun focusLost(e: FocusEvent) {
      setFocused(false)
    }
  }

  init {
    component.add(header)
    component.add(body)
    setHeaderToolbarTargetComponent(content)
    ListenerUtil.addFocusListener(component, focusListener)
  }

  override fun updateBlockContent(newContent: CombinedDiffBlockContent) {
    val viewer = newContent.viewer
    content = viewer.component
    setHeaderToolbarTargetComponent(content)
    if (!blockCollapsed) {
      body.setContent(content)
    }
    header.setContent(if (viewer is CombinedDiffLoadingBlock) pathOnlyHeader else headerWithToolbar)
    component.validate()
  }

  private fun toggleCollapse() {
    blockCollapsed = !blockCollapsed
  }

  private fun updateBodyContent(newCollapsingState: Boolean) {
    if (newCollapsingState) {
      body.setContent(null)
    }
    else {
      body.setContent(content)
    }
  }

  override fun dispose() {}
}

