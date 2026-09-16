// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiRecordHeader;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  @Override
  @Nullable PsiElement setupConstructor(@NotNull PsiClass aClass,
                                        @NotNull PsiNewExpression newExpression,
                                        @NotNull PsiExpressionList argList,
                                        @NotNull TemplateBuilder builder) {
    PsiRecordHeader header = aClass.getRecordHeader();
    CreateRecordFromNewFix.setupRecordComponents(header, builder, argList, getTargetSubstitutor(newExpression));
    return null;
  }

  @Override
  protected @Unmodifiable List<PsiClass> filterTargetClasses(PsiElement element, Project project) {
    return ContainerUtil.filter(super.filterTargetClasses(element, project), 
                                cls -> cls.getContainingClass() == null || cls.hasModifierProperty(PsiModifier.STATIC));
  }
}