package com.intellij.codeInsight.hint;

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author ven
 */
public class PrevNextParameterHandler extends EditorActionHandler {
  public PrevNextParameterHandler(boolean isNextParameterHandler) {
    myIsNextParameterHandler = isNextParameterHandler;
  }

  private boolean myIsNextParameterHandler;

  private static PsiElement getExpressionList(Editor editor, Project project) {
    int offset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return null;
    return ParameterInfoController.findArgumentList(file, offset, -1);
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiElement exprList = getExpressionList(editor, project);
    return exprList != null && ParameterInfoController.isAlreadyShown(editor, exprList.getTextRange().getStartOffset());
  }

  public void execute(Editor editor, DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiElement exprList = getExpressionList(editor, project);
    int listOffset = exprList.getTextRange().getStartOffset();
    if (myIsNextParameterHandler) {
      ParameterInfoController.nextParameter(editor, listOffset);
    }
    else {
      ParameterInfoController.prevParameter(editor, listOffset);
    }
  }
}