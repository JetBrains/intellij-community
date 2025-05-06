// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.editor.action

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.diff.DiscussionsViewOption
import com.intellij.collaboration.ui.codereview.editor.CodeReviewInEditorViewModel
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Internal
class CodeReviewInEditorToolbarActionGroup(private val vm: CodeReviewInEditorViewModel) : ActionGroup(), DumbAware {
  private val updateAction = UpdateAction()

  private val disableReviewAction =
    ViewOptionToggleAction(DiscussionsViewOption.DONT_SHOW,
                           CollaborationToolsBundle.message("review.editor.action.disable.text"))
  private val hideResolvedAction =
    ViewOptionToggleAction(DiscussionsViewOption.UNRESOLVED_ONLY,
                           CollaborationToolsBundle.message("review.editor.action.show.unresolved.text"))
  private val showAllAction =
    ViewOptionToggleAction(DiscussionsViewOption.ALL,
                           CollaborationToolsBundle.message("review.editor.action.show.all.text"))

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun getChildren(e: AnActionEvent?): Array<AnAction> =
    arrayOf(updateAction, Separator.create(), disableReviewAction, hideResolvedAction, showAllAction)

  override fun displayTextInToolbar(): Boolean = true

  override fun useSmallerFontForTextInToolbar(): Boolean = true

  init {
    with(templatePresentation) {
      isPopupGroup = true
      putClientProperty(ActionUtil.HIDE_DROPDOWN_ICON, true)
      description = CollaborationToolsBundle.message("review.editor.mode.description.title")
      val tooltip = HelpTooltip()
        .setTitle(CollaborationToolsBundle.message("review.editor.mode.description.title"))
        .setDescription(CollaborationToolsBundle.message("review.editor.mode.description"))
      putClientProperty(ActionButton.CUSTOM_HELP_TOOLTIP, tooltip)
    }
  }

  override fun update(e: AnActionEvent) {
    val shown = vm.discussionsViewOption.value != DiscussionsViewOption.DONT_SHOW
    val synced = !vm.updateRequired.value
    with(e.presentation) {
      if (shown) {
        text = CollaborationToolsBundle.message("review.editor.mode.title")
        icon = if (synced) null else getWarningIcon()
      }
      else {
        text = null
        icon = AllIcons.Actions.Preview
      }
    }
  }

  private fun getWarningIcon(): Icon = HighlightDisplayLevel.find(HighlightSeverity.WARNING)?.icon ?: AllIcons.General.Warning

  private inner class UpdateAction
    : DumbAwareAction(CollaborationToolsBundle.message("review.editor.action.update.text"), null, AllIcons.Actions.CheckOut) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = vm.updateRequired.value
    }

    override fun actionPerformed(e: AnActionEvent) = vm.updateBranch()
  }

  private inner class ViewOptionToggleAction(
    private val option: DiscussionsViewOption,
    text: @NlsActions.ActionText String,
  ) : ToggleAction(text), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      super.update(e)

      if (option == DiscussionsViewOption.DONT_SHOW) {
        e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Never
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean = vm.discussionsViewOption.value == option
    override fun setSelected(e: AnActionEvent, state: Boolean) = vm.setDiscussionsViewOption(option)
  }
}