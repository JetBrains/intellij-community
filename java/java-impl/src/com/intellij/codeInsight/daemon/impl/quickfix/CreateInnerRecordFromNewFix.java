// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public class CreateInnerRecordFromNewFix extends CreateInnerClassFromNewFix {
  public CreateInnerRecordFromNewFix(PsiNewExpression expr) {
    super(expr);
  }

  @Override
  protected @NotNull CreateClassKind getKind() {
    return CreateClassKind.RECORD;
  }

  @NotNull
  @Override
  TemplateBuilderImpl createConstructorTemplate(PsiClass aClass, PsiNewExpression newExpression, PsiExpressionList argList) {
    TemplateBuilderImpl templateBuilder = new TemplateBuilderImpl(aClass);
    PsiRecordHeader header = aClass.getRecordHeader();
    CreateRecordFromNewFix.setupRecordComponents(header, templateBuilder, argList, getTargetSubstitutor(newExpression));
    return templateBuilder;
  }

  @Override
  protected @Unmodifiable List<PsiClass> filterTargetClasses(PsiElement element, Project project) {
    return ContainerUtil.filter(super.filterTargetClasses(element, project), 
                                cls -> cls.getContainingClass() == null || cls.hasModifierProperty(PsiModifier.STATIC));
  }
}