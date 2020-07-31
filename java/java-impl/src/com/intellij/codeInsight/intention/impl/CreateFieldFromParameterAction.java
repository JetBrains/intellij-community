/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class CreateFieldFromParameterAction extends CreateFieldFromParameterActionBase {
  private final boolean myIsFix;

  public CreateFieldFromParameterAction() {
    // an intention should be available for regular methods only, because for constructors there will be quickfix
    this(false);
  }

  public CreateFieldFromParameterAction(boolean isFix) {
    myIsFix = isFix;
  }

  @Override
  protected boolean isAvailable(@NotNull PsiParameter parameter) {
    PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
      return false;
    }
    boolean isConstructor = ((PsiMethod)scope).isConstructor();
    if (myIsFix && !isConstructor) return false;
    PsiCodeBlock body = ((PsiMethod)scope).getBody();
    if (body == null) return false;
    if (!myIsFix && isConstructor && ReferencesSearch.search(parameter, new LocalSearchScope(body)).findFirst() == null) return false;
    final PsiType type = getSubstitutedType(parameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(parameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(parameter, type, targetClass, false) &&
           parameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  protected PsiType getSubstitutedType(@NotNull PsiParameter parameter) {
    return FieldFromParameterUtils.getSubstitutedType(parameter);
  }

  @Override
  protected void performRefactoring(@NotNull Project project,
                                    @NotNull PsiClass targetClass,
                                    @NotNull PsiMethod method,
                                    @NotNull PsiParameter myParameter,
                                    PsiType type,
                                    @NotNull String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal) {
    FieldFromParameterUtils.createFieldAndAddAssignment(project, targetClass, method, myParameter, type, fieldName, methodStatic, isFinal);
  }
}
