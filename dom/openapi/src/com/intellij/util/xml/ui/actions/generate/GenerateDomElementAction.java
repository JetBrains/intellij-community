package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;

/**
 * User: Sergey.Vasiliev
 */
public class GenerateDomElementAction extends BaseGenerateAction {
  private GenerateDomElementProvider myGenerateProvider;

  public GenerateDomElementAction(final GenerateDomElementProvider generateProvider) {
    super(new CodeInsightActionHandler() {
      public void invoke(final Project project, final Editor editor, final PsiFile file) {
        new WriteCommandAction(project, file) {
          protected void run(final Result result) throws Throwable {
            final DomElement element = generateProvider.generate(project, editor, file);
            generateProvider.navigate(element);
          }
        }.execute();
      }

      public boolean startInWriteAction() {
        return false;
      }
    });

    myGenerateProvider = generateProvider;
  }

  public void update(AnActionEvent event) {
    super.update(event);

    event.getPresentation().setText(myGenerateProvider.getDescription());
  }
}
