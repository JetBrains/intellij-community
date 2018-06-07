/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.psi.controlFlow;

import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class LocalsOrMyInstanceFieldsControlFlowPolicy implements ControlFlowPolicy {
  private static final LocalsOrMyInstanceFieldsControlFlowPolicy INSTANCE = new LocalsOrMyInstanceFieldsControlFlowPolicy();

  private LocalsOrMyInstanceFieldsControlFlowPolicy() {
  }

  @Override
  public PsiVariable getUsedVariable(@NotNull PsiReferenceExpression refExpr) {
    if (isLocalOrMyInstanceReference(refExpr)) {
      return ObjectUtils.tryCast(refExpr.resolve(), PsiVariable.class);
    }
    return null;
  }

  @Override
  public boolean isParameterAccepted(@NotNull PsiParameter psiParameter) {
    return true;
  }

  @Override
  public boolean isLocalVariableAccepted(@NotNull PsiLocalVariable psiVariable) {
    return true;
  }

  public static LocalsOrMyInstanceFieldsControlFlowPolicy getInstance() {
    return INSTANCE;
  }

  /**
   * @param variableReference variable reference to check
   * @return true if given variable reference refers to local variable or the field which participates in
   * definitive assignment analysis, as specified in JLS, chapter 16. The method does not check whether
   * the reference actually resolves to variable.
   */
  public static boolean isLocalOrMyInstanceReference(PsiReferenceExpression variableReference) {
    PsiExpression qualifierExpression = variableReference.getQualifierExpression();
    // JLS 16: "Such an assignment is defined to occur if and only if either the simple name of the variable
    // (or, for a field, its simple name qualified by this) occurs on the left hand side of an assignment operator"
    // Qualified this is not allowed by spec
    return qualifierExpression == null || (qualifierExpression instanceof PsiThisExpression &&
                                           ((PsiThisExpression)qualifierExpression).getQualifier() == null);
  }
}
