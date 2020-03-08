package com.intellij.ide.actionMacro.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import org.jetbrains.annotations.NotNull;

public class PlaySavedMacros extends AnAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(IdeBundle.message("popup.title.play.saved.macros"), new MacrosGroup(), e.getDataContext(), JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);
    final Project project = e.getProject();
    if (project != null ) {
      popup.showCenteredInCurrentWindow(project);
    } else {
      popup.showInFocusCenter();
    }
  }
}
