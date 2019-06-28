// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class QuickSwitchSchemeAction extends AnAction implements DumbAware {
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  protected static final Icon ourCurrentAction = AllIcons.Actions.Forward;

  protected static final Icon ourNotCurrentAction = new IconLoader.LazyIcon() {
    @Override
    protected Icon compute() {
      return EmptyIcon.create(AllIcons.Actions.Forward.getIconWidth(), AllIcons.Actions.Forward.getIconHeight());
    }
  };

  protected String myActionPlace = ActionPlaces.UNKNOWN;

  private final boolean myShowPopupWithNoActions;

  protected QuickSwitchSchemeAction() {
    this(false);
  }

  protected QuickSwitchSchemeAction(boolean showPopupWithNoActions) {
    myShowPopupWithNoActions = showPopupWithNoActions;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    DefaultActionGroup group = new DefaultActionGroup();
    fillActions(project, group, e.getDataContext());
    showPopup(e, group);
  }

  protected abstract void fillActions(Project project, @NotNull DefaultActionGroup group, @NotNull DataContext dataContext);

  private void showPopup(AnActionEvent e, DefaultActionGroup group) {
    if (!myShowPopupWithNoActions && group.getChildrenCount() == 0) return;
    JBPopupFactory.ActionSelectionAid aid = getAidMethod();

    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      getPopupTitle(e), group, e.getDataContext(), aid, true, null, -1,
      (a) -> a.getTemplatePresentation().getIcon() != AllIcons.Actions.Forward,
      myActionPlace);

    showPopup(e, popup);
  }

  protected void showPopup(AnActionEvent e, ListPopup popup) {
    Project project = e.getProject();
    if (project != null) {
      popup.showCenteredInCurrentWindow(project);
    }
    else {
      popup.showInBestPositionFor(e.getDataContext());
    }
  }

  protected JBPopupFactory.ActionSelectionAid getAidMethod() {
    return JBPopupFactory.ActionSelectionAid.NUMBERING;
  }

  protected String getPopupTitle(@NotNull AnActionEvent e) {
    return e.getPresentation().getText();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(CommonDataKeys.PROJECT) != null && isEnabled());
  }

  protected boolean isEnabled() {
    return true;
  }
}
