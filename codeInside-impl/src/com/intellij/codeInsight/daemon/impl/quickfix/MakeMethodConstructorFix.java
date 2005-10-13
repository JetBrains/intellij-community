package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;

/**
 * @author Alexey Kudravtsev
 */
public class MakeMethodConstructorFix implements IntentionAction {
  private final PsiMethod myMethod;

  public MakeMethodConstructorFix(PsiMethod method) {
    myMethod = method;
  }

  public String getText() {
    return QuickFixBundle.message("convert.method.to.constructor");
  }

  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() && myMethod.getReturnTypeElement() != null && myMethod.getManager().isInProject(myMethod);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    myMethod.getReturnTypeElement().delete();
  }

  public boolean isQuickFix() {
    return true;
  }

  public boolean startInWriteAction() {
    return true;
  }
}
