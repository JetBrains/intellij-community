// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus

internal class AutoscrollToSource : ProblemsViewToggleAction({ it.autoscrollToSource })
internal class OpenInPreviewTab : ProblemsViewToggleAction({ it.openInPreviewTab })
internal class ShowPreview : ProblemsViewToggleAction({ it.showPreview })
internal class GroupByToolId : ProblemsViewToggleAction({ it.groupByToolId })
internal class SortFoldersFirst : ProblemsViewToggleAction({ it.sortFoldersFirst })
internal class SortBySeverity : ProblemsViewToggleAction({ it.sortBySeverity })
internal class SortByName : ProblemsViewToggleAction({ it.sortByName })

@ApiStatus.Internal
abstract class ProblemsViewToggleAction(optionSupplier: (ProblemsViewPanel) -> Option?)
  : DumbAware, ToggleOptionAction({ it.project?.let{ProblemsView.getSelectedPanel(it)}?.let(optionSupplier) }) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
