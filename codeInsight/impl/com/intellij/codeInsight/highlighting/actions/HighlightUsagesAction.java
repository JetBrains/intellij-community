
package com.intellij.codeInsight.highlighting.actions;

import com.intellij.codeInsight.highlighting.HighlightUsagesHandler;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

public class HighlightUsagesAction extends AnAction {

  public HighlightUsagesAction() {
    setInjectedContext(true);
  }

  public void update(final AnActionEvent event) {
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();

    presentation.setEnabled(dataContext.getData(DataConstants.PROJECT) != null &&
                            dataContext.getData(DataConstants.EDITOR) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    final Editor editor = (Editor) e.getDataContext().getData(DataConstants.EDITOR);
    final Project project = (Project) e.getDataContext().getData(DataConstants.PROJECT);
    if (editor == null) return;
    String commandName = getTemplatePresentation().getText();
    if (commandName == null) commandName = "";

    CommandProcessor.getInstance().executeCommand(
      project,
      new Runnable() {
        public void run() {
          PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
          new HighlightUsagesHandler().invoke(project, editor, psiFile);
        }
      },
      commandName,
      null
    );
  }
}