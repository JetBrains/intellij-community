package com.intellij.ide.actionMacro.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.popup.JBPopupFactory;

/**
 * User: Evgeny.Zakrevsky
 * Date: 8/14/12
 */
public class PlaySavedMacros extends AnAction {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    JBPopupFactory.getInstance()
      .createActionGroupPopup("Play Saved Macros", new MacrosGroup(), e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
      .showCenteredInCurrentWindow(e.getProject());
  }
}
