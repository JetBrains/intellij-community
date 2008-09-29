package com.intellij.ui.navigation;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.ShadowAction;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

abstract class NavigationAction extends AnAction {

  private ShadowAction myShadow;

  protected NavigationAction(JComponent c, final String originalActionID) {
    final AnAction back = ActionManager.getInstance().getAction(originalActionID);
    myShadow = new ShadowAction(this, back, c);
  }

  public final void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(getHistory(e) != null);
    if (e.getPresentation().isEnabled()) {
      e.getPresentation().setIcon(getTemplatePresentation().getIcon());
      doUpdate(e);
    }
  }

  protected abstract void doUpdate(final AnActionEvent e);

  @Nullable
  protected static History getHistory(final AnActionEvent e) {
    return e.getData(History.KEY);
  }

}
