package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;

public class GenerateAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    DataContext dataContext = e.getDataContext();

    final ListPopup popup =
      JBPopupFactory.getInstance().createActionGroupPopup(CodeInsightBundle.message("generate.list.popup.title"),
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

    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null) {
      presentation.setEnabled(false);
      return;
    }

    boolean groupEmpty = ActionGroupUtil.isGroupEmpty(getGroup(), event);
    presentation.setEnabled(!groupEmpty);
  }

  private static DefaultActionGroup getGroup() {
    return (DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_GENERATE);
  }
}