package com.intellij.codeInsight.hint;

import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiJavaFile;

/**
 * @author ven
 */
public class PrevNextParameterHandler extends EditorActionHandler {
  public PrevNextParameterHandler(boolean isNextParameterHandler) {
    myIsNextParameterHandler = isNextParameterHandler;
  }

  private boolean myIsNextParameterHandler;

  private PsiExpressionList getExpressionList(Editor editor, Project project) {
    int offset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file instanceof PsiJavaFile) {
      return ParameterInfoController.findArgumentList(file, offset, -1);
    }

    return null;
  }

  public boolean isEnabled(Editor editor, DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiExpressionList exprList = getExpressionList(editor, project);
    if (exprList != null) {
      int listOffset = exprList.getTextRange().getStartOffset();
      return ParameterInfoController.isAlreadyShown(editor, listOffset);
    }
    return false;
  }

  public void execute(Editor editor, DataContext dataContext) {
    Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    PsiExpressionList exprList = getExpressionList(editor, project);
    int listOffset = exprList.getTextRange().getStartOffset();
    if (myIsNextParameterHandler) {
      ParameterInfoController.nextParameter(editor, listOffset);
    }
    else {
      ParameterInfoController.prevParameter(editor, listOffset);
    }
  }
}