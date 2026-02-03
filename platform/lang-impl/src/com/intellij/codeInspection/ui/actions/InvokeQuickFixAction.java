// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui.actions;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.ProblemDescriptionNode;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class InvokeQuickFixAction extends AnAction {
  private final InspectionResultsView myView;

  public InvokeQuickFixAction(final InspectionResultsView view) {
    super(InspectionsBundle.message(ExperimentalUI.isNewUI() ? "inspection.action.apply.quickfix.new" : "inspection.action.apply.quickfix"),
          InspectionsBundle.message("inspection.action.apply.quickfix.description"), AllIcons.Actions.IntentionBulb);
    myView = view;
    registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS).getShortcutSet(),
                              myView.getTree());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(myView.areFixesAvailable());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ActionGroup fixes = (ActionGroup)ActionManager.getInstance().getAction("QuickFixes");
    if (fixes.getChildren(e).length == 0) {
      Messages.showInfoMessage(myView, InspectionsBundle.message("there.are.no.applicable.quick.fixes.message"),
                               InspectionsBundle.message("nothing.found.to.fix.title"));
      return;
    }
    DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(InspectionsBundle.message("inspection.tree.popup.title"),
                              fixes,
                              dataContext,
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);
    InspectionResultsView.showPopup(e, popup);
  }
  static boolean canApplyFixes(@NotNull AnActionEvent e) {
    Object[] nodes = e.getData(PlatformCoreDataKeys.SELECTED_ITEMS);
    return nodes == null || !ContainerUtil.and(nodes, path -> path instanceof ProblemDescriptionNode);
  }
}
