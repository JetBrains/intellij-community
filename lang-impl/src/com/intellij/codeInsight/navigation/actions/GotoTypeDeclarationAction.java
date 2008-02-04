package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class GotoTypeDeclarationAction extends BaseCodeInsightAction implements CodeInsightActionHandler{

  protected CodeInsightActionHandler getHandler(){
    return this;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public void update(final AnActionEvent event) {
    if (Extensions.getExtensions(TypeDeclarationProvider.EP_NAME).length == 0) {
      event.getPresentation().setVisible(false);
    }
    else {
      super.update(event);
    }
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = editor.getCaretModel().getOffset();
    PsiElement symbolType = findSymbolType(editor, offset);
    if (symbolType == null) return;
    symbolType = symbolType.getNavigationElement();
    OpenFileDescriptor descriptor=new OpenFileDescriptor(project, symbolType.getContainingFile().getVirtualFile(), symbolType.getTextOffset());
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public boolean startInWriteAction() {
    return false;
  }

  @Nullable
  public static PsiElement findSymbolType(Editor editor, int offset) {
    PsiElement targetElement = TargetElementUtilBase.getInstance().findTargetElement(editor,
      TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
      TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
      TargetElementUtilBase.LOOKUP_ITEM_ACCEPTED,
      offset);
    for(TypeDeclarationProvider provider: Extensions.getExtensions(TypeDeclarationProvider.EP_NAME)) {
      PsiElement result = provider.getSymbolType(targetElement);
      if (result != null) return result;
    }

    return null;
  }

}