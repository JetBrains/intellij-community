package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.util.IncorrectOperationException;

public class SafeDeleteFix implements IntentionAction {
  private final PsiElement myElement;

  public SafeDeleteFix(PsiElement element) {
    myElement = element;
  }

  public String getText() {
    return QuickFixBundle.message("safe.delete.text",
                                  HighlightMessageUtil.getSymbolName(myElement, PsiSubstitutor.EMPTY));
  }

  public String getFamilyName() {
    return QuickFixBundle.message("safe.delete.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myElement.isValid() && myElement.getManager().isInProject(myElement);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(myElement.getContainingFile())) return;
    SafeDeleteHandler.invoke(project, new PsiElement[]{myElement}, false);
  }

  public boolean startInWriteAction() {
    return false;
  }

}
