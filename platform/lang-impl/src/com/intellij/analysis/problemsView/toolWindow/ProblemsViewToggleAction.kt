// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleOptionAction
import com.intellij.openapi.project.DumbAware
import java.util.function.Function

internal class AutoscrollToSource : ProblemsViewToggleAction({ it.autoscrollToSource })
internal class ShowPreview : ProblemsViewToggleAction({ it.showPreview })
internal class GroupByToolId : ProblemsViewToggleAction({ it.groupByToolId })
internal class SortFoldersFirst : ProblemsViewToggleAction({ it.sortFoldersFirst })
internal class SortBySeverity : ProblemsViewToggleAction({ it.sortBySeverity })
internal class SortByName : ProblemsViewToggleAction({ it.sortByName })

abstract class ProblemsViewToggleAction(optionSupplier: (ProblemsViewPanel) -> Option?)
  : DumbAware, ToggleOptionAction(Function { event: AnActionEvent -> event.getData(ProblemsViewPanel.SELECTED)?.let(optionSupplier) })
