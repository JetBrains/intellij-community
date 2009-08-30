/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
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
