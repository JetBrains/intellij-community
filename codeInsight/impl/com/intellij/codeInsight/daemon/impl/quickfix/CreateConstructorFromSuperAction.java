/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class CreateConstructorFromSuperAction extends CreateConstructorFromThisOrSuperAction {

  public CreateConstructorFromSuperAction(PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  protected String getSyntheticMethodName() {
    return "super";
  }

  protected PsiClass[] getTargetClasses(PsiElement element) {
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    } while (element instanceof PsiTypeParameter);
    PsiClass curClass = (PsiClass) element;
    if (curClass == null || curClass instanceof PsiAnonymousClass) return null;
    PsiClassType[] extendsTypes = curClass.getExtendsListTypes();
    if (extendsTypes.length == 0) return null;
    PsiClass aClass = extendsTypes[0].resolve();
    if (aClass instanceof PsiTypeParameter) return null;
    return aClass != null && aClass.isValid() && aClass.getManager().isInProject(aClass) ? new PsiClass[]{aClass} : null;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.from.super.call.family");
  }
}