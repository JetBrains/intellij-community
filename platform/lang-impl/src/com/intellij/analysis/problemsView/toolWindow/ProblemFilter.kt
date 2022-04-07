// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel.renderSeverity
import com.intellij.util.ui.tree.TreeUtil.promiseExpandAll
import org.jetbrains.annotations.Nls

internal class ProblemFilter(val state: ProblemsViewState) : (Problem) -> Boolean {
  override fun invoke(problem: Problem): Boolean {
    val highlighting = problem as? HighlightingProblem ?: return true
    return !(state.hideBySeverity.contains(highlighting.severity))
  }
}

internal class SeverityFiltersActionGroup : DumbAware, ActionGroup() {
  override fun getChildren(event: AnActionEvent?): Array<AnAction> {
    val project = event?.project ?: return AnAction.EMPTY_ARRAY
    if (project.isDisposed) return AnAction.EMPTY_ARRAY
    val panel = ProblemsView.getSelectedPanel(project) as? HighlightingPanel ?: return AnAction.EMPTY_ARRAY
    val severities = SeverityRegistrar.getSeverityRegistrar(project).allSeverities.reversed()
      .filter { it != HighlightSeverity.INFO && it > HighlightSeverity.INFORMATION && it < HighlightSeverity.ERROR }
    val (mainSeverities, otherSeverities) = severities.partition { it >= HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING }
    val actions = mainSeverities.mapTo(ArrayList<AnAction>()) {
      SeverityFilterAction(ProblemsViewBundle.message("problems.view.highlighting.severity.show", renderSeverity(it)), it.myVal, panel)
    }
    actions.add(OtherSeveritiesFilterAction(otherSeverities.map { it.myVal }, panel))
    return actions.toTypedArray()
  }
}

private abstract class SeverityFilterActionBase(name: @Nls String, protected val panel: HighlightingPanel): DumbAwareToggleAction(name) {
  abstract fun updateState(selected: Boolean): Boolean

  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    val changed = updateState(selected)
    if (changed) {
      val wasEmpty = panel.tree.isEmpty
      panel.state.intIncrementModificationCount()
      panel.treeModel.structureChanged(null)
      panel.powerSaveStateChanged()
      // workaround to expand a root without handle
      if (wasEmpty) {
        promiseExpandAll(panel.tree)
      }
    }
  }
}

private class OtherSeveritiesFilterAction(
  private val severities: Collection<Int>,
  panel: HighlightingPanel
): SeverityFilterActionBase(ProblemsViewBundle.message("problems.view.highlighting.other.problems.show"), panel) {
  override fun isSelected(event: AnActionEvent): Boolean {
    val state = panel.state.hideBySeverity
    return !severities.all { state.contains(it) }
  }

  override fun updateState(selected: Boolean): Boolean {
    val state = panel.state.hideBySeverity
    return when {
      selected -> state.removeAll(severities)
      else -> state.addAll(severities)
    }
  }
}

private class SeverityFilterAction(@Nls name: String, val severity: Int, panel: HighlightingPanel): SeverityFilterActionBase(name, panel) {
  override fun isSelected(event: AnActionEvent) = !panel.state.hideBySeverity.contains(severity)

  override fun updateState(selected: Boolean): Boolean {
    val state = panel.state.hideBySeverity
    return when {
      selected -> state.remove(severity)
      else -> state.add(severity)
    }
  }
}
