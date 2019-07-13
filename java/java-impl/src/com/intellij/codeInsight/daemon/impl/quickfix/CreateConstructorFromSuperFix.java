// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author ven
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

@Deprecated
@ScheduledForRemoval(inVersion = "2019.3")
public class CreateConstructorFromSuperFix extends CreateConstructorFromThisOrSuperFix {

  public CreateConstructorFromSuperFix(@NotNull PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  protected String getSyntheticMethodName() {
    return "super";
  }

  @Override
  @NotNull
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    do {
      element = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    while (element instanceof PsiTypeParameter);
    PsiClass curClass = (PsiClass)element;
    if (curClass == null || curClass instanceof PsiAnonymousClass) return Collections.emptyList();
    PsiClassType[] extendsTypes = curClass.getExtendsListTypes();
    if (extendsTypes.length == 0) return Collections.emptyList();
    PsiClass aClass = extendsTypes[0].resolve();
    if (aClass instanceof PsiTypeParameter) return Collections.emptyList();
    if (aClass != null && aClass.isValid() && canModify(aClass)) {
      return Collections.singletonList(aClass);
    }
    return Collections.emptyList();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.constructor.from.super.call.family");
  }
}