// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class NewElementAction extends DumbAwareAction implements PopupAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    createPopup(e.getDataContext())
      .showInBestPositionFor(e.getDataContext());
  }

  protected @NotNull ListPopup createPopup(@NotNull DataContext dataContext) {
    return JBPopupFactory.getInstance().createActionGroupPopup(
      getPopupTitle(),
      getGroup(dataContext),
      dataContext,
      getActionSelectionAid(),
      isShowDisabledActions(),
      getDisposeCallback(),
      getMaxRowCount(),
      getPreselectActionCondition(dataContext),
      getPlace());
  }

  protected @Nullable JBPopupFactory.ActionSelectionAid getActionSelectionAid() {
    return null;
  }

  protected int getMaxRowCount() {
    return -1;
  }

  protected @Nullable Condition<AnAction> getPreselectActionCondition(DataContext dataContext) {
    return LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext);
  }

  protected @Nullable Runnable getDisposeCallback() {
    return null;
  }

  protected boolean isShowDisabledActions() {
    return false;
  }

  protected @NlsContexts.PopupTitle String getPopupTitle() {
    return IdeBundle.message("title.popup.new.element");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (!isEnabled(e)) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(e.getDataContext()), e));
  }

  protected boolean isEnabled(@NotNull AnActionEvent e) {
    if (Boolean.TRUE.equals(e.getData(LangDataKeys.NO_NEW_ACTION))) {
      return false;
    }
    return true;
  }

  protected ActionGroup getGroup(DataContext dataContext) {
    var result = getCustomizedGroup();
    if (result == null) {
      result = (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
    }
    return result;
  }

  private static ActionGroup getCustomizedGroup() {
    // We can't just get a customized GROUP_WEIGHING_NEW because it's only customized as
    // a part of the Project View popup customization, so we get that and dig for the New subgroup.
    // There has to be a better way to do it...
    var projectViewPopupGroup = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_PROJECT_VIEW_POPUP);
    if (!(projectViewPopupGroup instanceof ActionGroup)) {
      return null;
    }
    for (AnAction child : ((ActionGroup)projectViewPopupGroup).getChildren(null)) {
       if (child instanceof ActionGroup childGroup && isNewElementGroup(childGroup)) {
         return childGroup;
       }
    }
    return null;
  }

  private static boolean isNewElementGroup(ActionGroup group) {
    if (group instanceof WeighingNewActionGroup) {
      return true;
    }
    if (group instanceof ActionGroupWrapper actionGroupWrapper) {
      return isNewElementGroup(actionGroupWrapper.getDelegate());
    }
    return false;
  }

  protected @NotNull String getPlace() {
    return ActionPlaces.getActionGroupPopupPlace(IdeActions.GROUP_WEIGHING_NEW);
  }
}
