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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;

public class MockIntroduceConstantHandler extends IntroduceConstantHandler{
  private final PsiClass myTargetClass;

  public MockIntroduceConstantHandler(final PsiClass targetClass) {
    myTargetClass = targetClass;
  }

  @Override
  protected Settings showRefactoringDialog(final Project project, final Editor editor, final PsiClass parentClass, final PsiExpression expr,
                                           final PsiType type, final PsiExpression[] occurrences, final PsiElement anchorElement,
                                           final PsiElement anchorElementIfAll) {
    return new Settings("xxx", expr, occurrences, true, true, true, InitializationPlace.IN_FIELD_DECLARATION, getVisibility(), null, type, false,
                        myTargetClass != null ? myTargetClass : parentClass, false, false);
  }

  protected String getVisibility() {
    return PsiModifier.PUBLIC;
  }
}
