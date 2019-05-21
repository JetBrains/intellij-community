// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@Deprecated
@ScheduledForRemoval(inVersion = "2019.3")
public class CreateConstructorFromThisFix extends CreateConstructorFromThisOrSuperFix {

  public CreateConstructorFromThisFix(@NotNull PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  protected String getSyntheticMethodName() {
    return "this";
  }

  @Override
  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    PsiElement e = element;
    do {
      e = PsiTreeUtil.getParentOfType(e, PsiClass.class);
    } while (e instanceof PsiTypeParameter);
    if (e != null && e.isValid() && canModify(e)) {
      return Collections.singletonList((PsiClass)e);
    }
    else {
      return Collections.emptyList();
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.from.this.call.family");
  }
}