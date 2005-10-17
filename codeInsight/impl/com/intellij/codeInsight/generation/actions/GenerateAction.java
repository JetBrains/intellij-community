package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.actions.ShowPopupMenuAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListPopup;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;

public class GenerateAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    ListPopup popup = ActionListPopup.createListPopup(CodeInsightBundle.message("generate.list.popup.title"),
                                                      getGroup(), dataContext, false, false);
    Component focusOwner=(Component)dataContext.getData(DataConstantsEx.CONTEXT_COMPONENT);
    Point location = ShowPopupMenuAction.getPopupLocation(focusOwner, dataContext);
    SwingUtilities.convertPointToScreen(location, focusOwner);
    popup.show(location.x, location.y);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }

    Editor editor = (Editor)dataContext.getData(DataConstants.EDITOR);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    boolean groupEmpty = ActionListPopup.isGroupEmpty(getGroup(), event, new HashMap<AnAction,Presentation>());
    presentation.setEnabled(!groupEmpty);
  }

  private DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE);
  }
}