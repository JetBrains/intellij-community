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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Aug 6, 2002
 * Time: 6:16:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.psi.controlFlow;

import com.intellij.psi.*;

public class LocalsOrMyInstanceFieldsControlFlowPolicy implements ControlFlowPolicy {
  private static final LocalsOrMyInstanceFieldsControlFlowPolicy INSTANCE = new LocalsOrMyInstanceFieldsControlFlowPolicy();

  private LocalsOrMyInstanceFieldsControlFlowPolicy() {
  }

  @Override
  public PsiVariable getUsedVariable(PsiReferenceExpression refExpr) {
    PsiExpression qualifier = refExpr.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression) {
      PsiElement resolved = refExpr.resolve();
      if (!(resolved instanceof PsiVariable)) return null;
      return (PsiVariable)resolved;
    }

    return null;
  }

  @Override
  public boolean isParameterAccepted(PsiParameter psiParameter) {
    return true;
  }

  @Override
  public boolean isLocalVariableAccepted(PsiLocalVariable psiVariable) {
    return true;
  }

  public static LocalsOrMyInstanceFieldsControlFlowPolicy getInstance() {
    return INSTANCE;
  }
}
