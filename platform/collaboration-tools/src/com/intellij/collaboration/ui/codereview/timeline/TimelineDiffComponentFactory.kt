// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui.codereview.timeline

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.codereview.comment.RoundedPanel
import com.intellij.collaboration.ui.util.ActivatableCoroutineScopeProvider
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.ListLayout
import com.intellij.util.PathUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.NonNls
import java.awt.Dimension
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

object TimelineDiffComponentFactory {

  fun createDiffComponent(project: Project, editorFactory: EditorFactory,
                          text: CharSequence, modifyEditor: (EditorEx) -> Unit): JComponent =
    EditorHandlerPanel.create(editorFactory) { factory ->
      val editor = createSimpleDiffEditor(project, factory, text)
      modifyEditor(editor)
      editor
    }

  private fun createSimpleDiffEditor(project: Project, editorFactory: EditorFactory, text: CharSequence): EditorEx {
    return (editorFactory.createViewer(editorFactory.createDocument(text), project, EditorKind.DIFF) as EditorEx).apply {
      gutterComponentEx.setPaintBackground(false)

      setHorizontalScrollbarVisible(true)
      setVerticalScrollbarVisible(false)
      setCaretEnabled(false)
      isEmbeddedIntoDialogWrapper = true
      contentComponent.isOpaque = false

      setBorder(JBUI.Borders.empty())

      settings.apply {
        isCaretRowShown = false
        additionalLinesCount = 0
        additionalColumnsCount = 0
        isRightMarginShown = false
        setRightMargin(-1)
        isFoldingOutlineShown = false
        isIndentGuidesShown = false
        isVirtualSpace = false
        isWheelFontChangeEnabled = false
        isAdditionalPageAtBottom = false
        lineCursorWidth = 1
      }
    }
  }

  fun wrapWithHeader(diffComponent: JComponent,
                     filePath: @NonNls String,
                     collapsibleState: StateFlow<Boolean>,
                     collapsedState: MutableStateFlow<Boolean>,
                     onFileClick: () -> Unit): JComponent {
    val scopeProvider = ActivatableCoroutineScopeProvider()

    val expandCollapseButton = InlineIconButton(EmptyIcon.ICON_16).apply {
      actionListener = ActionListener {
        collapsedState.update { !it }
      }
    }

    scopeProvider.launchInScope {
      expandCollapseButton.bindVisibility(this, collapsibleState)
    }

    scopeProvider.launchInScope {
      collapsedState.collect {
        expandCollapseButton.icon = if (it) {
          AllIcons.General.ExpandComponent
        }
        else {
          AllIcons.General.CollapseComponent
        }
        expandCollapseButton.hoveredIcon = if (it) {
          AllIcons.General.ExpandComponentHover
        }
        else {
          AllIcons.General.CollapseComponentHover
        }
        //TODO: tooltip?
      }
    }

    diffComponent.border = IdeBorderFactory.createBorder(SideBorder.TOP)

    scopeProvider.launchInScope {
      diffComponent.bindVisibility(this, collapsedState.map { !it })
    }

    return RoundedPanel(ListLayout.vertical(0), 8).apply {
      isOpaque = false
      add(createFileNameComponent(filePath, expandCollapseButton, onFileClick))
      add(diffComponent)
    }.also {
      scopeProvider.activateWith(it)
    }
  }

  private fun createFileNameComponent(filePath: String, expandCollapseButton: JComponent, onFileClick: () -> Unit): JComponent {
    val name = PathUtil.getFileName(filePath)
    val path = PathUtil.getParentPath(filePath)
    val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(name)

    val nameLabel = LinkLabel<Unit>(name, fileType.icon) { _, _ ->
      onFileClick()
    }
    return JPanel(MigLayout(LC().insets("0").gridGap("5", "0").fill().noGrid())).apply {
      border = JBUI.Borders.empty(10)
      CollaborationToolsUIUtil.overrideUIDependentProperty(this) {
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
      }

      add(nameLabel)

      if (!path.isBlank()) add(JLabel(path).apply {
        foreground = UIUtil.getContextHelpForeground()
      }, CC().minWidth("0"))

      add(expandCollapseButton, CC().hideMode(3).gapLeft("10:push"))
    }
  }
}