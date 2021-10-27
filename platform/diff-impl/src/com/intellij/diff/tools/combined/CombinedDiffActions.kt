// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.actions.impl.SetEditorSettingsAction
import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.text.SmartTextDiffProvider
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ToggleActionButton

internal class CombinedToggleExpandByDefaultAction(val textSettings: TextDiffSettingsHolder.TextDiffSettings,
                                                   val foldingModels: List<FoldingModelSupport>) :
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
    foldingModels.forEach { it.expandAll(expand) }
  }
}

internal class CombinedIgnorePolicySettingAction(settings: TextDiffSettingsHolder.TextDiffSettings) :
  TextDiffViewerUtil.IgnorePolicySettingAction(settings, *SmartTextDiffProvider.IGNORE_POLICIES)

internal class CombinedHighlightPolicySettingAction(settings: TextDiffSettingsHolder.TextDiffSettings) :
  TextDiffViewerUtil.HighlightPolicySettingAction(settings, *SmartTextDiffProvider.HIGHLIGHT_POLICIES)

internal class CombinedEditorSettingsAction(private val settings: TextDiffSettingsHolder.TextDiffSettings,
                                            private val foldingModels: List<FoldingModelSupport>,
                                            editors: List<Editor>) : SetEditorSettingsAction(settings, editors) {
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
