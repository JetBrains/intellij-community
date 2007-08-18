
package com.intellij.codeInsight.completion.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class CodeCompletionGroup extends DefaultActionGroup {

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null){
      presentation.setEnabled(false);
      return;
    }

    Editor editor = DataKeys.EDITOR.getData(dataContext);
    if (editor == null){
      presentation.setEnabled(false);
      return;
    }
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null){
      presentation.setEnabled(false);
      return;
    }

    presentation.setEnabled(true);
  }
}
