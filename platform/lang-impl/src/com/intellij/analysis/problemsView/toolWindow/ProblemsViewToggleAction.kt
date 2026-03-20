// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware

internal class AutoscrollToSource : ProblemsViewToggleAction({ panel, event -> panel.getAutoscrollToSource(event) })
internal class OpenInPreviewTab : ProblemsViewToggleAction({ panel, event -> panel.getOpenInPreviewTab(event) })
internal class ShowPreview : ProblemsViewToggleAction({ panel, event -> panel.getShowPreview(event) })
internal class GroupByToolId : ProblemsViewToggleAction({ panel, _ -> panel.groupByToolId })
internal class SortFoldersFirst : ProblemsViewToggleAction({ panel, _ -> panel.sortFoldersFirst })
internal class SortBySeverity : ProblemsViewToggleAction({ panel, _ -> panel.sortBySeverity })
internal class SortByName : ProblemsViewToggleAction({ panel, _ -> panel.sortByName })

internal abstract class ProblemsViewToggleAction(optionSupplier: (ProblemsViewPanel, AnActionEvent) -> Option?)
  : DumbAware, ToggleOptionAction({ event ->
    event.getData(ProblemsViewPanel.DATA_KEY)?.let { panel ->
      optionSupplier(panel, event)
    } 
  }) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
