/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CreateInnerRecordFromNewFix extends CreateInnerClassFromNewFix {
  public CreateInnerRecordFromNewFix(PsiNewExpression expr) {
    super(expr);
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
    CreateRecordFromNewFix.setupRecordComponents(header, templateBuilder, argList, getTargetSubstitutor(newExpression));
    return templateBuilder;
  }

  @Override
  protected List<PsiClass> filterTargetClasses(PsiElement element, Project project) {
    return ContainerUtil.filter(super.filterTargetClasses(element, project), 
                                cls -> cls.getContainingClass() == null || cls.hasModifierProperty(PsiModifier.STATIC));
  }
}