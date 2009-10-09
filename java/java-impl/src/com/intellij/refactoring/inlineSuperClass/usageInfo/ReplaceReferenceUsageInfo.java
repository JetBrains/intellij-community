/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;

public class ReplaceReferenceUsageInfo extends FixableUsageInfo {
  public static final Logger LOG = Logger.getInstance("#" + ReplaceReferenceUsageInfo.class.getName());
  private final PsiClass myTargetClass;
  private final String myConflict;

  public ReplaceReferenceUsageInfo(PsiElement referenceExpression, PsiClass[] targetClasses) {
    super(referenceExpression);
    myTargetClass = targetClasses[0];
    myConflict = targetClasses.length > 1 ? referenceExpression.getText() + "can be replaced with any of " + StringUtil.join(targetClasses, new Function<PsiClass, String>() {
      public String fun(final PsiClass psiClass) {
        return psiClass.getQualifiedName();
      }
    }, ", ") : null;
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiElement referenceExpression = getElement();
    if (referenceExpression != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
      referenceExpression.replace(referenceExpression instanceof PsiReferenceExpression ? elementFactory.createReferenceExpression(myTargetClass) : elementFactory.createClassReferenceElement(myTargetClass));
    }
  }

  @Override
  public String getConflictMessage() {
    return myConflict;
  }
}