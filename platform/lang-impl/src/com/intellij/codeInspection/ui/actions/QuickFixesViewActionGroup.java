// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.QuickFixAction;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.ui.actions.InspectionViewActionBase.getToolWrapper;
import static com.intellij.codeInspection.ui.actions.InspectionViewActionBase.getView;

/**
 * @author Dmitry Batkovich
 */
@ApiStatus.Internal
public final class QuickFixesViewActionGroup extends ActionGroup {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    InspectionResultsView view = getView(e);
    if (view == null || !InvokeQuickFixAction.canApplyFixes(e)) return AnAction.EMPTY_ARRAY;
    InspectionToolWrapper toolWrapper = getToolWrapper(e);
    if (toolWrapper == null) return AnAction.EMPTY_ARRAY;
    InspectionTree tree = view.getTree();
    CommonProblemDescriptor[] selectedDescriptors = tree.getSelectedDescriptors(e);
    QuickFixAction[] quickFixes = view.getProvider().getCommonQuickFixes(toolWrapper, tree,
                                                                         selectedDescriptors,
                                                                         InspectionTree.getSelectedRefElements(e));
    if (quickFixes.length != 0) return quickFixes;
    return view.getProvider().getPartialQuickFixes(toolWrapper, tree, selectedDescriptors);
  }
}
