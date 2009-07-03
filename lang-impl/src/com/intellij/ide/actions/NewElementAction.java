package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

public class NewElementAction extends AnAction implements DumbAware {

  public void actionPerformed(final AnActionEvent e) {
    showPopup(e.getDataContext());
  }

  protected void showPopup(DataContext dataContext) {
    final ListPopup popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(IdeBundle.message("title.popup.new.element"),
                              getGroup(),
                              dataContext,
                              false, false, false,
                              null, -1, LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext));

    popup.showInBestPositionFor(dataContext);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (ideView == null) {
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(!ActionGroupUtil.isGroupEmpty(getGroup(), event));
  }

  private static ActionGroup getGroup() {
    return (ActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_WEIGHING_NEW);
  }
}
