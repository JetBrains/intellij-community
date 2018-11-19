/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.engine;

import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Nikolay.Tropin
 */
public class JavaSimpleGetterProvider implements SimplePropertyGetterProvider {
  @Override
  public boolean isInsideSimpleGetter(@NotNull PsiElement element) {
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    if (method == null) return false;

    final PsiCodeBlock body = method.getBody();
    if(body == null){
      return false;
    }

    final PsiStatement[] statements = body.getStatements();
    if(statements.length != 1){
      return false;
    }

    final PsiStatement statement = statements[0];
    if(!(statement instanceof PsiReturnStatement)){
      return false;
    }

    final PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
    if(!(value instanceof PsiReferenceExpression)){
      return false;
    }

    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    final PsiExpression qualifier = reference.getQualifierExpression();
    if(qualifier != null && !"this".equals(qualifier.getText())) {
      return false;
    }

    final PsiElement referent = reference.resolve();
    if(referent == null) {
      return false;
    }

    if(!(referent instanceof PsiField)) {
      return false;
    }

    return Comparing.equal(((PsiField)referent).getContainingClass(), method.getContainingClass());
  }
}
