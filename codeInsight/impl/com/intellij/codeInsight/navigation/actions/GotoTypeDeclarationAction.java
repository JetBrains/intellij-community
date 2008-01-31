package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

/**
 *
 */
public class GotoTypeDeclarationAction extends BaseCodeInsightAction implements CodeInsightActionHandler{

  protected CodeInsightActionHandler getHandler(){
    return this;
  }

  /*
  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file instanceof PsiJavaFile;
  }
  */

  protected boolean isValidForLookup() {
    return true;
  }

  public void invoke(final Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    int offset = getOffset(editor);
    PsiElement symbolType = findSymbolType(editor, offset);
    if (symbolType == null) return;
    symbolType = symbolType.getNavigationElement();
    OpenFileDescriptor descriptor=new OpenFileDescriptor(project, symbolType.getContainingFile().getVirtualFile(), symbolType.getTextOffset());
    FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
  }

  public boolean startInWriteAction() {
    return false;
  }

  public static PsiElement findSymbolType(Editor editor, int offset) {
    PsiElement targetElement = TargetElementUtilBase.getInstance().findTargetElement(editor,
      TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED |
      TargetElementUtilBase.ELEMENT_NAME_ACCEPTED |
      TargetElementUtilBase.LOOKUP_ITEM_ACCEPTED,
      offset);
    PsiType type;
    if (targetElement instanceof PsiVariable){
      type = ((PsiVariable)targetElement).getType();
    }
    else if (targetElement instanceof PsiMethod){
      type = ((PsiMethod)targetElement).getReturnType();
    }
    else{
      return null;
    }
    if (type == null) return null;
    return PsiUtil.resolveClassInType(type);
  }

  protected int getOffset(Editor editor) {
    return editor.getCaretModel().getOffset();
  }

  /*
  public void update(AnActionEvent event, Presentation presentation) {
    super.update(event, presentation);
    if (!ActionPlaces.MAIN_MENU.equals(event.getPlace())) {
      presentation.setText("Go to " + getTemplatePresentation().getText());
    }
  }
  */
}