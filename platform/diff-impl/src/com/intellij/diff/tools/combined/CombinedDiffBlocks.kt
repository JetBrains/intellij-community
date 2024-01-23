// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.panels.OpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.FontUtil
import com.intellij.util.IconUtil.getIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.Icon
import javax.swing.JComponent

class CombinedBlockProducer(
  val id: CombinedBlockId,
  val producer: DiffRequestProducer
)

interface CombinedBlockId

interface CombinedDiffBlock<ID : CombinedBlockId> : Disposable {
  val id: ID

  val header: JComponent
  val stickyHeader: JComponent

  val body: JComponent
  val component: JComponent

  fun updateBlockContent(newContent: CombinedDiffBlockContent) {}
}

class CombinedDiffBlockContent(val viewer: FrameDiffTool.DiffViewer, val blockId: CombinedBlockId)

interface CombinedDiffBlockFactory<ID : CombinedBlockId> {
  fun createBlock(project: Project, content: CombinedDiffBlockContent): CombinedDiffBlock<ID>
}

class CombinedSimpleDiffBlockFactory : CombinedDiffBlockFactory<CombinedPathBlockId> {
  override fun createBlock(project: Project,
                           content: CombinedDiffBlockContent): CombinedDiffBlock<CombinedPathBlockId> =
    with(content.blockId as CombinedPathBlockId) {
      CombinedSimpleDiffBlock(project, this, content.viewer.component, content.viewer is CombinedDiffLoadingBlock)
    }
}

class CombinedSimpleDiffHeader(project: Project,
                               blockId: CombinedPathBlockId,
                               withPathOnly: Boolean) : BorderLayoutPanel() {
  init {
    background = CombinedDiffUI.BLOCK_HEADER_BACKGROUND

    addToCenter(if (withPathOnly) createTextComponent(project, blockId.path) else buildToolbar(project, blockId).component)
    border = JBUI.Borders.empty(CombinedDiffUI.BLOCK_HEADER_INSETS)
  }

  private fun buildToolbar(project: Project, blockId: CombinedPathBlockId): ActionToolbar {
    val path = blockId.path
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(CombinedOpenInEditorAction(path))
    toolbarGroup.addSeparator()
    toolbarGroup.add(SelectableFilePathLabel(project, path))

    val toolbar = ActionManager.getInstance().createActionToolbar("CombinedDiffBlockHeaderToolbar", toolbarGroup, true)
    toolbar.targetComponent = this
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.component.background = CombinedDiffUI.BLOCK_HEADER_BACKGROUND
    toolbar.component.border = JBUI.Borders.empty()

    return toolbar
  }

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
      val component = OpaquePanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(3), 0))
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

data class CombinedPathBlockId(val path: FilePath, val fileStatus: FileStatus?, val tag: Any? = null) : CombinedBlockId

internal class CombinedSimpleDiffBlock(project: Project,
                                       override val id: CombinedPathBlockId,
                                       initialContent: JComponent,
                                       isPathOnlyHeader: Boolean
) : CombinedDiffBlock<CombinedPathBlockId>,
    CombinedDiffRoundedPanel(VerticalFlowLayout(VerticalFlowLayout.TOP, 0, 0, true, true), CombinedDiffUI.BLOCK_ARC) {

  private val pathOnlyHeader: CombinedSimpleDiffHeader = CombinedSimpleDiffHeader(project, id, true)
  private val headerWithToolbar: CombinedSimpleDiffHeader = CombinedSimpleDiffHeader(project, id, false)

  override val header: Wrapper = Wrapper(if (isPathOnlyHeader) pathOnlyHeader else headerWithToolbar)

  override val stickyHeader: JComponent = CombinedDiffRoundedPanel(BorderLayout(0, 0), CombinedDiffUI.BLOCK_ARC, true)
    .apply {
      add(CombinedSimpleDiffHeader(project, id, false), BorderLayout.CENTER)
    }

  override val body: Wrapper = Wrapper(initialContent)

  private var editors: List<Editor> = emptyList()

  override fun getPreferredSize(): Dimension {
    val preferredSize = super.getPreferredSize()
    if (editors.isEmpty()) return preferredSize

    // TODO: investigate why sometimes the size of the editor's viewport is calculated incorrectly
    var someError = 0
    editors.forEach { e ->
      someError = maxOf((e as EditorEx).gutterComponentEx.preferredSize.height - body.targetComponent.preferredSize.height, someError)
    }
    if (someError > 0) {
      preferredSize.height += someError
    }
    return preferredSize
  }


  init {
    add(header)
    add(body)
    isOpaque = true
  }

  override fun updateBlockContent(newContent: CombinedDiffBlockContent) {
    val viewer = newContent.viewer
    editors = viewer.editors
    body.setContent(viewer.component)
    header.setContent(if (viewer is CombinedDiffLoadingBlock) pathOnlyHeader else headerWithToolbar)
    validate()
  }

  override val component = this
  override fun dispose() {}
}

