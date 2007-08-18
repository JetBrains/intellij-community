package com.intellij.ide.actions;

import com.intellij.codeInsight.javadoc.JavaDocManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

public class ExternalJavaDocAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = DataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      Messages.showMessageDialog(
        project,
        IdeBundle.message("message.please.select.element.for.javadoc"),
        IdeBundle.message("title.no.element.selected"),
        Messages.getErrorIcon()
      );
      return;
    }


    PsiFile context = DataKeys.PSI_FILE.getData(dataContext);
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    PsiElement originalElement = (context!=null && editor!=null)? context.findElementAt(editor.getCaretModel().getOffset()):null;
    try {
      element.putUserData(
        JavaDocManager.ORIGINAL_ELEMENT_KEY,
        SmartPointerManager.getInstance(originalElement.getProject()).createSmartPsiElementPointer(originalElement)
      );
    } catch(RuntimeException ex) {
      // some UserDataHolder does not support putUserData, e.g. PsiPackage
      // tolerate it
    }

    JavaDocManager.getInstance(project).openJavaDoc(element);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Editor editor = DataKeys.EDITOR.getData(dataContext);
    final PsiElement element = DataKeys.PSI_ELEMENT.getData(dataContext);

    if (editor != null) {
      Project project = DataKeys.PROJECT.getData(dataContext);
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      boolean enabled = (file instanceof PsiJavaFile || PsiUtil.isInJspFile(file) ||
                        (file!=null && JavaDocManager.getProviderFromElement(file)!=null
                        )) &&
                        element != null &&
                        JavaDocManager.getExternalJavaDocUrl(element) != null;
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
    else{
      presentation.setEnabled(
        element != null &&
        JavaDocManager.getExternalJavaDocUrl(
          element
        ) != null
      );
      presentation.setVisible(true);
    }
  }
}