/**
 * @author cdr
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

public class InsertNewFix implements IntentionAction {
  private final PsiMethodCallExpression myMethodCall;
  private final PsiClass myClass;

  public InsertNewFix(PsiMethodCallExpression methodCall, PsiClass aClass) {
    myMethodCall = methodCall;
    myClass = aClass;
  }

  public String getText() {
    return QuickFixBundle.message("insert.new.fix");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethodCall != null
    && myMethodCall.isValid()
    && myMethodCall.getManager().isInProject(myMethodCall);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(myMethodCall.getContainingFile())) return;
    PsiElementFactory factory = JavaPsiFacade.getInstance(myMethodCall.getProject()).getElementFactory();
    PsiNewExpression newExpression = (PsiNewExpression)factory.createExpressionFromText("new X()",null);

    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    assert classReference != null;
    classReference.replace(factory.createClassReferenceElement(myClass));
    PsiExpressionList argumentList = newExpression.getArgumentList();
    assert argumentList != null;
    argumentList.replace(myMethodCall.getArgumentList());
    myMethodCall.replace(newExpression);
  }

  public boolean startInWriteAction() {
    return true;
  }
}