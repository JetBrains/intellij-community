// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.actions.impl.*
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.text.SmartTextDiffProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.pom.Navigatable

internal class CombinedNextBlockAction(private val context: DiffContext) : NextChangeAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    if (DiffUtil.isFromShortcut(e)) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    e.presentation.icon = AllIcons.Actions.Play_last
    e.presentation.isVisible = true
    e.presentation.isEnabled = context.getCombinedDiffNavigation()?.canGoNextBlock() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val navigation = context.getCombinedDiffNavigation() ?: return
    if (!navigation.canGoNextBlock()) return

    navigation.goNextBlock()
  }
}

internal class CombinedPrevBlockAction(private val context: DiffContext) : PrevChangeAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    if (DiffUtil.isFromShortcut(e)) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    e.presentation.icon = AllIcons.Actions.Play_first
    e.presentation.isVisible = true
    e.presentation.isEnabled = context.getCombinedDiffNavigation()?.canGoPrevBlock() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val navigation = context.getCombinedDiffNavigation() ?: return
    if (!navigation.canGoPrevBlock()) return

    navigation.goPrevBlock()
  }
}

internal class CombinedNextDifferenceAction(private val context: DiffContext) : NextDifferenceAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    if (DiffUtil.isFromShortcut(e)) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    e.presentation.icon = AllIcons.Actions.Play_forward
    e.presentation.isVisible = true
    e.presentation.isEnabled = context.getCombinedDiffNavigation()?.canGoNextDiff() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val navigation = context.getCombinedDiffNavigation() ?: return
    if (!navigation.canGoNextDiff()) return

    navigation.goNextDiff()
  }
}

internal class CombinedPrevDifferenceAction(private val context: DiffContext) : PrevDifferenceAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    if (DiffUtil.isFromShortcut(e)) {
      e.presentation.isEnabledAndVisible = true
      return
    }

    val navigation = context.getCombinedDiffNavigation()
    e.presentation.icon = AllIcons.Actions.Play_back
    e.presentation.isVisible = true
    e.presentation.isEnabled = navigation?.canGoPrevDiff() ?: false
  }

  override fun actionPerformed(e: AnActionEvent) {
    val navigation = context.getCombinedDiffNavigation() ?: return
    if (!navigation.canGoPrevDiff()) return

    navigation.goPrevDiff()
  }
}

private fun DiffContext.getCombinedDiffNavigation(): CombinedDiffNavigation? = getUserData(COMBINED_DIFF_VIEWER_KEY)

internal class CombinedToggleExpandByDefaultAction(private val textSettings: TextDiffSettingsHolder.TextDiffSettings,
                                                   private val foldingModels: () -> List<FoldingModelSupport>) :
  DumbAwareToggleAction(message("collapse.unchanged.fragments"), null, null) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = textSettings.contextRange != -1
  }

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

internal class CombinedOpenInEditorAction(private val path: FilePath) : OpenInEditorAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = DiffUtil.canNavigateToFile(e.project, path.virtualFile)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val navigatable = findNavigatable(project) ?: return
    openEditor(project, navigatable, null)
  }

  private fun findNavigatable(project: Project?): Navigatable? {
    val file = path.virtualFile
    if (!DiffUtil.canNavigateToFile(project, file)) return null

    return PsiNavigationSupport.getInstance().createNavigatable(project!!, file!!, 0)
  }
}
