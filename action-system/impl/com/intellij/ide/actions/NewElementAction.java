package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

public class NewElementAction extends AnAction {

  public void actionPerformed(final AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(IdeBundle.message("title.popup.new.element"),
                              getGroup(),
                              dataContext,
                              JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                              false);
    popup.showInBestPositionFor(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    IdeView ideView = DataKeys.IDE_VIEW.getData(dataContext);
    if (ideView == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(), event));
  }

  private DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_NEW);
  }
}
