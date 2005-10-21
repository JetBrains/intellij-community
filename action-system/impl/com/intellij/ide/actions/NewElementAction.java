package com.intellij.ide.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionListPopup;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ListPopup;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class NewElementAction extends AnAction {
  private final Map<AnAction,Presentation> myAction2presentationMap = new HashMap<AnAction,Presentation>();

  public void actionPerformed(final AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    ListPopup popup =  ActionListPopup.createListPopup(IdeBundle.message("title.popup.new.element"), getGroup(), dataContext, false, false);
    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    JComponent focusOwner=(JComponent)focusManager.getFocusOwner();
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
    IdeView ideView = (IdeView)dataContext.getData(DataConstantsEx.IDE_VIEW);
    if (ideView == null) {
      presentation.setEnabled(false);
      return;
    }
    try {
      final boolean groupEmpty = ActionListPopup.isGroupEmpty(getGroup(), event, myAction2presentationMap);
      presentation.setEnabled(!groupEmpty);
    }
    finally {
      myAction2presentationMap.clear();
    }
  }

  private DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_NEW);
  }
}
