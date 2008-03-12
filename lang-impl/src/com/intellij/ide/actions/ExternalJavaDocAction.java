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
import org.jetbrains.annotations.Nullable;

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
    PsiElement originalElement = getOriginalElement(context, editor);
    DocumentationManager.storeOriginalElement(project, originalElement, element);
    final ExtensibleDocumentationProvider provider = (ExtensibleDocumentationProvider)DocumentationManager.getProviderFromElement(element);
    assert provider != null;
    provider.openExternalDocumentation(element, originalElement);
  }

  @Nullable
  private static PsiElement getOriginalElement(final PsiFile context, final Editor editor) {
    return (context!=null && editor!=null)? context.findElementAt(editor.getCaretModel().getOffset()):null;
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    DataContext dataContext = event.getDataContext();
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
    final PsiElement originalElement = getOriginalElement(LangDataKeys.PSI_FILE.getData(dataContext), editor);
    DocumentationManager.storeOriginalElement(PlatformDataKeys.PROJECT.getData(dataContext), originalElement, element);
    final DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
    boolean enabled = provider instanceof ExtensibleDocumentationProvider && ((ExtensibleDocumentationProvider)provider).isExternalDocumentationEnabled(element, originalElement);
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