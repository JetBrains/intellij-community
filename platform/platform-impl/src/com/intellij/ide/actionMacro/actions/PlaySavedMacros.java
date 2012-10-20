package com.intellij.ide.actionMacro.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

/**
 * User: Evgeny.Zakrevsky
 * Date: 8/14/12
 */
public class PlaySavedMacros extends AnAction {
  @Override
  public void actionPerformed(final AnActionEvent e) {
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup("Play Saved Macros", new MacrosGroup(), e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);
    final Project project = e.getProject();
    if (project != null ) {
      popup.showCenteredInCurrentWindow(project);
    } else {
      popup.showInFocusCenter();
    }
  }
}
