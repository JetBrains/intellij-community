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
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Max Medvedev
 */
public class CreateFieldFromParameterAction extends CreateFieldFromParameterActionBase {

  @Override
  protected boolean isAvailable(PsiParameter psiParameter) {
    final PsiType type = getSubstitutedType(psiParameter);
    final PsiClass targetClass = PsiTreeUtil.getParentOfType(psiParameter, PsiClass.class);
    return FieldFromParameterUtils.isAvailable(psiParameter, type, targetClass, false) &&
           psiParameter.getLanguage().isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  protected PsiType getSubstitutedType(PsiParameter parameter) {
    return FieldFromParameterUtils.getSubstitutedType(parameter);
  }

  @Override
  protected void performRefactoring(Project project,
                                    PsiClass targetClass,
                                    PsiMethod method,
                                    PsiParameter myParameter,
                                    PsiType type,
                                    String fieldName,
                                    boolean methodStatic,
                                    boolean isFinal) {
    FieldFromParameterUtils.createFieldAndAddAssignment(project, targetClass, method, myParameter, type, fieldName, methodStatic, isFinal);
  }
}
