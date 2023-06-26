// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class QuickSwitchSchemeAction extends AnAction implements DumbAware {
  private final static Condition<? super AnAction> DEFAULT_PRESELECT_ACTION = a -> {
    return a.getTemplatePresentation().getIcon() != AllIcons.Actions.Forward;
  };

  protected static final Icon ourNotCurrentAction = IconLoader.createLazy(() -> {
    return EmptyIcon.create(AllIcons.Actions.Forward.getIconWidth(), AllIcons.Actions.Forward.getIconHeight());
  });

  protected String myActionPlace;

  private final boolean myShowPopupWithNoActions;

  protected QuickSwitchSchemeAction() {
    this(false);
  }

  protected QuickSwitchSchemeAction(boolean showPopupWithNoActions) {
    myShowPopupWithNoActions = showPopupWithNoActions;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
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
      preselectAction(), myActionPlace);

    showPopup(e, popup);
  }

  @Nullable
  protected Condition<? super AnAction> preselectAction() {
    return DEFAULT_PRESELECT_ACTION;
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

  @Nls(capitalization = Nls.Capitalization.Title)
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
