// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Dimension
import javax.swing.JComponent

private const val MAX_TARGET_DISPLAY_LENGTH = 80
const val EXECUTION_TARGETS_COMBO_ADDITIONAL_ACTIONS_GROUP = "ExecutionTargets.Additional"
@JvmField val EXECUTION_TARGETS_COMBO_ACTION_PLACE = ActionPlaces.getPopupPlace("ExecutionTargets")

/**
 * Combo-box for selecting execution targets ([ExecutionTarget])
 *
 * See [com.intellij.execution.actions.RunConfigurationsComboBoxAction] for reference
 */
@ApiStatus.Internal
class ExecutionTargetComboBoxAction : ComboBoxAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    val presentation = e.presentation
    if (project == null ||
        project.isDisposed ||
        !project.isOpen ||
        RunManager.IS_RUN_MANAGER_INITIALIZED[project] != true) {
      presentation.isEnabledAndVisible = false
      return
    }

    val executionTarget = ExecutionTargetManager.getActiveTarget(project)
    if (executionTarget == DefaultExecutionTarget.INSTANCE || executionTarget.isExternallyManaged) {
      presentation.isEnabledAndVisible = false
      return
    }

    presentation.isEnabledAndVisible = true
    val name = StringUtil.trimMiddle(executionTarget.displayName, MAX_TARGET_DISPLAY_LENGTH)
    presentation.setText(name, false)
  }

  override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
    val actionGroup = DefaultActionGroup()

    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return actionGroup

    actionGroup.addAll(getTargetActions(project))

    val additionalActions = ActionManager.getInstance().getAction(EXECUTION_TARGETS_COMBO_ADDITIONAL_ACTIONS_GROUP) as? ActionGroup
    if (additionalActions != null) {
      actionGroup.add(additionalActions)
    }

    return actionGroup
  }

  private fun getTargetActions(project: Project): List<AnAction> {
    val selectedConfiguration = RunManager.getInstance(project).selectedConfiguration ?: return emptyList()

    val targets = ExecutionTargetManager.getTargetsToChooseFor(project, selectedConfiguration.configuration)
    val activeTarget = ExecutionTargetManager.getActiveTarget(project)

    val targetsGroups = targets.groupBy { it.groupName }
    val actions = mutableListOf<AnAction>()

    val defaultGroup = targetsGroups[null]
    if (defaultGroup != null) {
      actions.addAll(getTargetGroupActions(project, defaultGroup, null, activeTarget))
    }

    for ((name, targetsGroup) in targetsGroups.entries.sortedBy { it.key }) {
      if (name == null) continue
      actions.addAll(getTargetGroupActions(project, targetsGroup, name, activeTarget))
    }

    return actions
  }

  private fun getTargetGroupActions(project: Project,
                                    targets: List<ExecutionTarget>,
                                    targetGroupName: @Nls String?,
                                    activeTarget: ExecutionTarget): List<AnAction> {
    val actions = mutableListOf<AnAction>()
    if (targetGroupName != null) {
      actions.add(Separator.create(targetGroupName))
    }

    targets.forEach { actions.add(SelectTargetAction(project, it, it == activeTarget, it.isReady)) }
    return actions
  }

  override fun createActionPopup(group: DefaultActionGroup, context: DataContext, disposeCallback: Runnable?): ListPopup {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      myPopupTitle,
      group,
      context,
      null,
      shouldShowDisabledActions(),
      disposeCallback,
      maxRows,
      preselectCondition,
      EXECUTION_TARGETS_COMBO_ACTION_PLACE
    )
    popup.setMinimumSize(Dimension(minWidth, minHeight))
    return popup
  }

  override fun getPreselectCondition(): Condition<AnAction> =
    Condition { if (it is SelectTargetAction) it.isSelected else false }

  override fun shouldShowDisabledActions(): Boolean =
    true

  private class SelectTargetAction(private val project: Project,
                                   private val target: ExecutionTarget,
                                   val isSelected: Boolean,
                                   private val isReady: Boolean) : DumbAwareAction() {
    init {
      val name = target.displayName
      templatePresentation.setText(name, false)
      templatePresentation.description = ExecutionBundle.message("select.0", name)
      templatePresentation.icon = target.icon
    }

    override fun getActionUpdateThread(): ActionUpdateThread =
      ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = isReady
    }

    override fun actionPerformed(e: AnActionEvent) {
      ExecutionTargetManager.setActiveTarget(project, target)
    }
  }
}


