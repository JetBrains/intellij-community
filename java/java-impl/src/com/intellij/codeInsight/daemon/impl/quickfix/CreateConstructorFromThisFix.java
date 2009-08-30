/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class CreateConstructorFromThisFix extends CreateConstructorFromThisOrSuperFix {

  public CreateConstructorFromThisFix(PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  protected String getSyntheticMethodName() {
    return "this";
  }

  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    PsiElement e = element;
    do {
      e = PsiTreeUtil.getParentOfType(e, PsiClass.class);
    } while (e instanceof PsiTypeParameter);
    if (e != null && e.isValid() && e.getManager().isInProject(e)) {
      return Collections.singletonList((PsiClass)e);
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.from.this.call.family");
  }
}