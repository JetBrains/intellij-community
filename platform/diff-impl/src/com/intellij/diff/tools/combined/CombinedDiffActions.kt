// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.DiffContext
import com.intellij.diff.actions.impl.DiffNextFileAction
import com.intellij.diff.actions.impl.DiffPreviousFileAction
import com.intellij.diff.actions.impl.SetEditorSettingsActionGroup
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.text.SmartTextDiffProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction

internal class CombinedNextBlockAction(private val context: DiffContext) : DumbAwareAction() {
  init {
    copyFrom(this, DiffNextFileAction.ID)
  }

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

internal class CombinedPrevBlockAction(private val context: DiffContext) : DumbAwareAction() {
  init {
    copyFrom(this, DiffPreviousFileAction.ID)
  }

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

internal class CombinedNextDifferenceAction(private val context: DiffContext) : DumbAwareAction() {
  init {
    copyFrom(this, IdeActions.ACTION_NEXT_DIFF)
  }

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

internal class CombinedPrevDifferenceAction(private val context: DiffContext) : DumbAwareAction() {
  init {
    copyFrom(this, IdeActions.ACTION_PREVIOUS_DIFF)
  }

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

internal class CombinedEditorSettingsActionGroup(private val settings: TextDiffSettingsHolder.TextDiffSettings,
                                                 private val foldingModels: () -> List<FoldingModelSupport>,
                                                 editors: () -> List<Editor>) : SetEditorSettingsActionGroup(settings, editors) {
  init {
    templatePresentation.putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val diffModesSettingsGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_MODES)
    val editorSettingsGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_SETTINGS)
    val ignorePolicyGroup = CombinedIgnorePolicySettingAction(settings).actions.apply {
      add(Separator.create(message("option.ignore.policy.group.name")), Constraints.FIRST)
    }
    val highlightPolicyGroup = CombinedHighlightPolicySettingAction(settings).actions.apply {
      add(Separator.create(message("option.highlighting.policy.group.name")), Constraints.FIRST)
    }

    val isRightToolbarPlace = e != null && e.place.endsWith(ActionPlaces.DIFF_RIGHT_TOOLBAR)
    val isGutterPlace = e != null && !isRightToolbarPlace

    val actions = mutableListOf<AnAction>()
    if (isRightToolbarPlace) {
      actions.add(diffModesSettingsGroup)
    }
    actions.add(editorSettingsGroup)
    actions.add(CombinedToggleExpandByDefaultAction(settings, foldingModels))
    actions.addAll(myActions)
    actions.add(Separator.getInstance())
    actions.add(ignorePolicyGroup)
    actions.add(Separator.getInstance())
    actions.add(highlightPolicyGroup)
    actions.add(Separator.getInstance())
    actions.add(ActionManager.getInstance().getAction(IdeActions.ACTION_CONTEXT_HELP))

    if (isGutterPlace) {
      val gutterGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_DIFF_EDITOR_GUTTER_POPUP) as ActionGroup
      val result = arrayListOf(*gutterGroup.getChildren(e))
      result.add(Separator.getInstance())
      replaceOrAppend(result, editorSettingsGroup, DefaultActionGroup(actions))
      return result.toTypedArray()
    }

    return actions.toTypedArray()
  }
}

//
// Block global navigation
//

/**
 * Represent global block action.
 *
 * In contrast to [CombinedDiffBaseEditorForEachCaretHandler] actions,
 * are not bound to particular [com.intellij.openapi.editor.Editor] instance and works even for collapsed blocks.
 */
internal abstract class CombinedGlobalBlockNavigationAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  final override fun update(e: AnActionEvent) {
    val combinedDiffViewer = e.getData(COMBINED_DIFF_VIEWER)

    e.presentation.isEnabledAndVisible = combinedDiffViewer != null
  }
}

internal class CombinedCaretToNextBlockAction : CombinedGlobalBlockNavigationAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val viewer = e.getRequiredData(COMBINED_DIFF_VIEWER)
    if (viewer.canGoNextBlock()) {
      viewer.moveCaretToNextBlock()
    }
  }
}

internal class CombinedCaretToPrevBlockAction : CombinedGlobalBlockNavigationAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val viewer = e.getRequiredData(COMBINED_DIFF_VIEWER)
    if (viewer.canGoPrevBlock()) {
      viewer.moveCaretToPrevBlock()
    }
  }
}

internal class CombinedToggleBlockCollapseAction : CombinedGlobalBlockNavigationAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val viewer = e.getRequiredData(COMBINED_DIFF_VIEWER)
    viewer.toggleBlockCollapse()
  }
}

internal class CombinedToggleBlockCollapseAllAction : DumbAwareAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val combinedDiffViewer = e.getData(DiffDataKeys.DIFF_CONTEXT)?.getUserData(COMBINED_DIFF_VIEWER_KEY)
    val enabledAndVisible = combinedDiffViewer != null
    e.presentation.isEnabledAndVisible = enabledAndVisible
  }

  override fun actionPerformed(e: AnActionEvent) {
    val context = e.getRequiredData(DiffDataKeys.DIFF_CONTEXT)
    val viewer = context.getUserData(COMBINED_DIFF_VIEWER_KEY) ?: return

    viewer.collapseAllBlocks()
  }
}
