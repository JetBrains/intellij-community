// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiRecordHeader;
import org.jetbrains.annotations.NotNull;

public class CreateRecordFromNewFix extends CreateClassFromNewFix {
  public CreateRecordFromNewFix(PsiNewExpression newExpression) {
    super(newExpression);
  }

  @NotNull
  @Override
  protected CreateClassKind getKind() {
    return CreateClassKind.RECORD;
  }

  @NotNull
  @Override
  TemplateBuilderImpl createConstructorTemplate(PsiClass aClass, PsiNewExpression newExpression, PsiExpressionList argList) {
    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
    PsiRecordHeader header = aClass.getRecordHeader();
    CreateFromUsageUtils.setupRecordComponents(header, templateBuilder, argList, getTargetSubstitutor(newExpression));
    return templateBuilder;
  }
}
