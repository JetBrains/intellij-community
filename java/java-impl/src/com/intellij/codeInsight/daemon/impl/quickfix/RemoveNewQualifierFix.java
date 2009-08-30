package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 29, 2002
 * Time: 3:02:17 PM
 * To change this template use Options | File Templates.
 */
public class RemoveNewQualifierFix implements IntentionAction {
  private final PsiNewExpression expression;
  private final PsiClass aClass;

  public RemoveNewQualifierFix(PsiNewExpression expression, PsiClass aClass) {
    this.expression = expression;
    this.aClass = aClass;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("remove.qualifier.fix");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
        expression != null
        && expression.isValid()
        && (aClass == null || aClass.isValid())
        && expression.getManager().isInProject(expression);
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(expression.getContainingFile())) return;
    PsiJavaCodeReferenceElement classReference = expression.getClassReference();
    expression.getQualifier().delete();
    if (aClass != null && classReference != null) {
      classReference.bindToElement(aClass);
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

}
