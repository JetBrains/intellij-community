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
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

public abstract class QuickSwitchSchemeAction extends AnAction implements DumbAware {
  private static final Condition<? super AnAction> DEFAULT_PRESELECT_ACTION = a -> {
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
    var count = group.getChildrenCount();
    if (!myShowPopupWithNoActions && count == 0) return;

    JBPopupFactory.ActionSelectionAid aid = getAidMethod();
    if (aid == JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING) {
      // Exclude separators. Do it only here to avoid getting children unless necessary.
      count = (int) Arrays.stream(group.getChildren(e)).filter(child -> !(child instanceof Separator)).count();
      // Alphanumeric mnemonics are pointless with <= 10 items and don't work well with huge lists.
      if (count < 11 || count > 36) {
        aid = JBPopupFactory.ActionSelectionAid.NUMBERING;
      }
    }

    ListPopup popup = JBPopupFactory.getInstance().createActionGroupPopup(
      getPopupTitle(e), group, e.getDataContext(), aid, true, null, -1,
      preselectAction(), myActionPlace);

    showPopup(e, popup);
  }

  protected @Nullable Condition<? super AnAction> preselectAction() {
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
    return Registry.is("ide.quick.switch.alpha.numbering", false)
           ? JBPopupFactory.ActionSelectionAid.ALPHA_NUMBERING
           : JBPopupFactory.ActionSelectionAid.NUMBERING;
  }

  protected @Nls(capitalization = Nls.Capitalization.Title) String getPopupTitle(@NotNull AnActionEvent e) {
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
