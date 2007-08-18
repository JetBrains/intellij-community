package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.navigation.IncrementalSearchHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

public class IncrementalSearchAction extends AnAction{
  public IncrementalSearchAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null) return;

    new IncrementalSearchHandler().invoke(project, editor);
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
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }
}