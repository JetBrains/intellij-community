/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler;
import com.intellij.refactoring.introduceField.LocalToFieldHandler;

/**
 * @author ven
 */
public class MockLocalToFieldHandler extends LocalToFieldHandler {
  private final boolean myMakeEnumConstant;
  public MockLocalToFieldHandler(Project project, boolean isConstant, final boolean makeEnumConstant) {
    super(project, isConstant);
    myMakeEnumConstant = makeEnumConstant;
  }

  @Override
  protected BaseExpressionToFieldHandler.Settings showRefactoringDialog(PsiClass aClass, PsiLocalVariable local, PsiExpression[] occurences,
                                                                        boolean isStatic) {
    return new BaseExpressionToFieldHandler.Settings("xxx", null, occurences, true, isStatic, true, BaseExpressionToFieldHandler.InitializationPlace.IN_FIELD_DECLARATION,
                                                     PsiModifier.PRIVATE, local, local.getType(), false, aClass, true, myMakeEnumConstant);
  }
}
