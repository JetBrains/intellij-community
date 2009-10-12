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
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.IntroduceParameterRefactoring;
import com.intellij.refactoring.RefactoringImpl;
import com.intellij.refactoring.introduceParameter.IntroduceParameterProcessor;

/**
 * @author dsl
 */
public class IntroduceParameterRefactoringImpl extends RefactoringImpl<IntroduceParameterProcessor>
  implements IntroduceParameterRefactoring {

  private IntroduceParameterRefactoringImpl(Project project,
                                            PsiMethod methodToReplaceIn,
                                            PsiMethod methodToSearchFor,
                                            String parameterName, PsiExpression parameterInitializer,
                                            PsiExpression expressionToSearch, PsiLocalVariable localVariable,
                                            boolean removeLocalVariable, boolean declareFinal, final boolean replaceAllOccurences) {
    super(
      new IntroduceParameterProcessor(project, methodToReplaceIn, methodToSearchFor,
                                      parameterInitializer, expressionToSearch, localVariable, removeLocalVariable, parameterName, replaceAllOccurences,
                                      REPLACE_FIELDS_WITH_GETTERS_INACCESSIBLE, declareFinal, false, null, null));
  }

  IntroduceParameterRefactoringImpl(Project project,
                                           PsiMethod methodToReplaceIn,
                                           PsiMethod methodToSearchFor,
                                           String parameterName, PsiExpression parameterInitializer,
                                           PsiLocalVariable localVariable,
                                           boolean removeLocalVariable, boolean declareFinal) {
    this(project, methodToReplaceIn, methodToSearchFor, parameterName, parameterInitializer, null, localVariable, removeLocalVariable, declareFinal, false);

  }

  IntroduceParameterRefactoringImpl(Project project,
                                    PsiMethod methodToReplaceIn,
                                    PsiMethod methodToSearchFor,
                                    String parameterName, PsiExpression parameterInitializer,
                                    PsiExpression expressionToSearchFor,
                                    boolean declareFinal, final boolean replaceAllOccurences) {
    this(project, methodToReplaceIn, methodToSearchFor, parameterName, parameterInitializer, expressionToSearchFor, null, false, declareFinal, replaceAllOccurences);

  }

  public void enforceParameterType(PsiType forcedType) {
    myProcessor.setForcedType(forcedType);
  }

  public void setFieldReplacementPolicy(int policy) {
    myProcessor.setReplaceFieldsWithGetters(policy);
  }

  public PsiType getForcedType() {
    return myProcessor.getForcedType();
  }

  public int getFieldReplacementPolicy() {
    return myProcessor.getReplaceFieldsWithGetters();
  }
}
