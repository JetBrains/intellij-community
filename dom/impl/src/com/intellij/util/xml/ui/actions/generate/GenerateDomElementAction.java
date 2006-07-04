package com.intellij.util.xml.ui.actions.generate;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementNavigationProvider;
import com.intellij.util.xml.DomElementsNavigationManager;

/**
 * User: Sergey.Vasiliev
 */
public class GenerateDomElementAction extends BaseGenerateAction {
  private GenerateDomElementProvider myGenerateProvider;

  public GenerateDomElementAction(final GenerateDomElementProvider generateProvider) {
   super(new CodeInsightActionHandler() {
      public void invoke(Project project, Editor editor, PsiFile file) {
        final DomElement element = generateProvider.generate(project, editor, file);

        generateProvider.navigate(element);
      }

      public boolean startInWriteAction() {
        return true;
      }
    });

    myGenerateProvider = generateProvider;
  }

  public void update(AnActionEvent event) {
    super.update(event);

    event.getPresentation().setText(myGenerateProvider.getDescription());
  }
}
