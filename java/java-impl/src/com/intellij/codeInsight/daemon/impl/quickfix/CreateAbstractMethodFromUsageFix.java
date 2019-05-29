// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Deprecated
@ScheduledForRemoval(inVersion = "2019.3")
public class CreateAbstractMethodFromUsageFix extends CreateMethodFromUsageFix {
  public CreateAbstractMethodFromUsageFix(@NotNull PsiMethodCallExpression methodCall) {
    super(methodCall);
  }

  @Override
  protected String getDisplayString(String name) {
    return QuickFixBundle.message("create.abstract.method.from.usage.text", name);
  }

  @NotNull
  @Override
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    List<PsiClass> result = new ArrayList<>();
    PsiReferenceExpression expr = getMethodCall().getMethodExpression();
    for (PsiClass each : super.getTargetClasses(element)) {
      if (PsiUtil.isAbstractClass(each) && !each.isInterface() && !shouldCreateStaticMember(expr, each)) result.add(each);
    }
    return result;
  }

  @Override
  protected String getVisibility(PsiClass parentClass, @NotNull PsiClass targetClass) {
    String result = super.getVisibility(parentClass, targetClass);
    return PsiModifier.PUBLIC.equals(result) ? result : PsiModifier.PROTECTED;
  }

  @Override
  protected boolean shouldBeAbstract(PsiReferenceExpression expression, PsiClass targetClass) {
    return true;
  }
}
