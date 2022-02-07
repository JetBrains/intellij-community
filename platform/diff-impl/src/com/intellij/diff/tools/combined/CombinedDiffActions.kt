// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.actions.impl.NextChangeAction
import com.intellij.diff.actions.impl.OpenInEditorAction
import com.intellij.diff.actions.impl.PrevChangeAction
import com.intellij.diff.actions.impl.SetEditorSettingsAction
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.text.SmartTextDiffProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ToolbarLabelAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.vcs.FilePath
import com.intellij.pom.Navigatable
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.ToggleActionButton
import java.awt.Component
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

internal class CombinedToggleExpandByDefaultAction(val textSettings: TextDiffSettingsHolder.TextDiffSettings,
                                                   val foldingModels: () -> List<FoldingModelSupport>) :
  ToggleActionButton(message("collapse.unchanged.fragments"), null), DumbAware {

  override fun isVisible(): Boolean = textSettings.contextRange != -1

  override fun isSelected(e: AnActionEvent): Boolean = !textSettings.isExpandByDefault

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val expand = !state
    if (textSettings.isExpandByDefault == expand) return
    textSettings.isExpandByDefault = expand
    expandAll(expand)
  }

  private fun expandAll(expand: Boolean) {
    foldingModels().forEach { it.expandAll(expand) }
  }
}

internal class CombinedIgnorePolicySettingAction(settings: TextDiffSettingsHolder.TextDiffSettings) :
  TextDiffViewerUtil.IgnorePolicySettingAction(settings, *SmartTextDiffProvider.IGNORE_POLICIES)

internal class CombinedHighlightPolicySettingAction(settings: TextDiffSettingsHolder.TextDiffSettings) :
  TextDiffViewerUtil.HighlightPolicySettingAction(settings, *SmartTextDiffProvider.HIGHLIGHT_POLICIES)

internal class CombinedEditorSettingsAction(private val settings: TextDiffSettingsHolder.TextDiffSettings,
                                            private val foldingModels: () -> List<FoldingModelSupport>,
                                            editors: () -> List<Editor>) : SetEditorSettingsAction(settings, editors) {
  init {
    templatePresentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, true)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val editorSettingsGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_SETTINGS)
    val ignorePolicyGroup = CombinedIgnorePolicySettingAction(settings).actions.apply {
      add(Separator.create(message("option.ignore.policy.group.name")), Constraints.FIRST)
    }
    val highlightPolicyGroup = CombinedHighlightPolicySettingAction(settings).actions.apply {
      add(Separator.create(message("option.highlighting.policy.group.name")), Constraints.FIRST)
    }

    val actions = mutableListOf<AnAction>()
    actions.add(editorSettingsGroup)
    actions.add(CombinedToggleExpandByDefaultAction(settings, foldingModels))
    actions.addAll(myActions)
    actions.add(Separator.getInstance())
    actions.add(ignorePolicyGroup)
    actions.add(Separator.getInstance())
    actions.add(highlightPolicyGroup)
    actions.add(Separator.getInstance())
    actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP))

    if (e != null && !e.place.endsWith(ActionPlaces.DIFF_RIGHT_TOOLBAR)) {
      val gutterGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_GUTTER_POPUP) as ActionGroup
      val result = arrayListOf(*gutterGroup.getChildren(e))
      result.add(Separator.getInstance())
      replaceOrAppend(result, editorSettingsGroup, DefaultActionGroup(actions))
      return result.toTypedArray()
    }

    return actions.toTypedArray()
  }
}

internal class CombinedPrevNextFileAction(private val blockId: CombinedPathBlockId,
                                          private val toolbar: Component?,
                                          private val next: Boolean) : ToolbarLabelAction(), RightAlignedToolbarAction {
  private val text = message(if (next) "action.Combined.Diff.NextChange.text" else "action.Combined.Diff.PrevChange.text")

  init {
    ActionUtil.copyFrom(this, if (next) NextChangeAction.ID else PrevChangeAction.ID)
    templatePresentation.icon = null
    templatePresentation.text = HtmlBuilder().appendLink("", text).toString()
    templatePresentation.description = null //disable label tooltip
  }

  override fun update(e: AnActionEvent) {
    val combinedDiffViewer = e.getData(COMBINED_DIFF_VIEWER)
    if (combinedDiffViewer == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val newPosition = if (next) combinedDiffViewer.nextBlockPosition() else combinedDiffViewer.prevBlockPosition()
    e.presentation.isVisible = newPosition != -1
  }

  override fun actionPerformed(e: AnActionEvent) {
    val combinedDiffViewer = e.getData(COMBINED_DIFF_VIEWER) ?: return
    val newPosition = if (next) combinedDiffViewer.nextBlockPosition() else combinedDiffViewer.prevBlockPosition()
    if (newPosition != -1) {
      combinedDiffViewer.selectDiffBlock(newPosition, ScrollPolicy.DIFF_BLOCK)
    }
  }

  override fun createHyperlinkListener(): HyperlinkListener = object : HyperlinkAdapter() {

    override fun hyperlinkActivated(e: HyperlinkEvent) {
      val place = (toolbar as? ActionToolbarImpl)?.place ?: ActionPlaces.DIFF_TOOLBAR
      val event = AnActionEvent.createFromAnAction(this@CombinedPrevNextFileAction, e.inputEvent, place,
                                                   ActionToolbar.getDataContextFor(toolbar))
      actionPerformed(event)
    }
  }

  private fun CombinedDiffViewer.prevBlockPosition(): Int {
    val curPosition = curBlockPosition()
    return if (curPosition != -1 && curPosition >= 1) curPosition - 1 else -1
  }

  private fun CombinedDiffViewer.nextBlockPosition(): Int {
    val curPosition = curBlockPosition()
    return if (curPosition != -1 && curPosition < diffBlocks.size - 1) curPosition + 1 else -1
  }

  private fun CombinedDiffViewer.curBlockPosition(): Int = diffBlocksPositions[blockId] ?: -1

  override fun isCopyable(): Boolean = true

  override fun getHyperlinkTooltip(): String = text
}

internal class CombinedOpenInEditorAction(private val path: FilePath) : OpenInEditorAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = DiffUtil.canNavigateToFile(e.project, path.virtualFile)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    findNavigatable(project)?.run { openEditor(project, this) }
  }

  private fun findNavigatable(project: Project?): Navigatable? {
    val file = path.virtualFile
    if (!DiffUtil.canNavigateToFile(project, file)) return null

    return PsiNavigationSupport.getInstance().createNavigatable(project!!, file!!, 0)
  }
}
