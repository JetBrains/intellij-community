package com.intellij.ide.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.IdeBundle;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.lang.documentation.ExtensibleDocumentationProvider;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;

public class ExternalJavaDocAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    if (element == null) {
      Messages.showMessageDialog(
        project,
        IdeBundle.message("message.please.select.element.for.javadoc"),
        IdeBundle.message("title.no.element.selected"),
        Messages.getErrorIcon()
      );
      return;
    }


    PsiFile context = LangDataKeys.PSI_FILE.getData(dataContext);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    PsiElement originalElement = (context!=null && editor!=null)? context.findElementAt(editor.getCaretModel().getOffset()):null;
    try {
      element.putUserData(
        DocumentationManager.ORIGINAL_ELEMENT_KEY,
        SmartPointerManager.getInstance(originalElement.getProject()).createSmartPsiElementPointer(originalElement)
      );
    } catch(RuntimeException ex) {
      // some UserDataHolder does not support putUserData, e.g. PsiPackage
      // tolerate it
    }

    final ExtensibleDocumentationProvider provider = (ExtensibleDocumentationProvider)DocumentationManager.getProviderFromElement(element);
    provider.openExternalDocumentation(element);
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    boolean enabled = provider instanceof ExtensibleDocumentationProvider && ((ExtensibleDocumentationProvider)provider).isExternalDocumentationEnabled(element);
    if (editor != null) {
      presentation.setEnabled(enabled);
      presentation.setVisible(enabled);
    }
    else{
      presentation.setEnabled(enabled);
      presentation.setVisible(true);
    }
  }
}