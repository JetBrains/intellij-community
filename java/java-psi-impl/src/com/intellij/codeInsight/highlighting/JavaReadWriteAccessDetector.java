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
package com.intellij.codeInsight.highlighting;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class JavaReadWriteAccessDetector extends ReadWriteAccessDetector {
  @Override
  public boolean isReadWriteAccessible(@NotNull final PsiElement element) {
    return element instanceof PsiVariable && !(element instanceof ImplicitVariable) || element instanceof PsiClass || element instanceof PsiAnnotationMethod && !(element instanceof PsiCompiledElement);
  }

  @Override
  public boolean isDeclarationWriteAccess(@NotNull final PsiElement element) {
    if (element instanceof PsiVariable && ((PsiVariable)element).getInitializer() != null) {
      return true;
    }
    if (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiForeachStatement) {
      return true;
    }
    return false;
  }

  @NotNull
  @Override
  public Access getReferenceAccess(@NotNull final PsiElement referencedElement, @NotNull final PsiReference reference) {
    return getExpressionAccess(reference.getElement());
  }

  @NotNull
  @Override
  public Access getExpressionAccess(@NotNull final PsiElement expression) {
    if (!(expression instanceof PsiExpression)) {
      if (expression instanceof PsiNameValuePair || expression instanceof PsiIdentifier) {
        return Access.Write;
      }
      return Access.Read;
    }
    PsiExpression expr = (PsiExpression) expression;
    boolean readAccess = PsiUtil.isAccessedForReading(expr);
    boolean writeAccess = PsiUtil.isAccessedForWriting(expr);
    if (!writeAccess && expr instanceof PsiReferenceExpression) {
      //when searching usages of fields, should show all found setters as a "only write usage"
      PsiElement actualReferee = ((PsiReferenceExpression) expr).resolve();
      if (actualReferee instanceof PsiMethod && PropertyUtilBase.isSimplePropertySetter((PsiMethod)actualReferee)) {
        writeAccess = true;
        readAccess = false;
      }
    }
    if (writeAccess && readAccess) return Access.ReadWrite;
    return writeAccess ? Access.Write : Access.Read;
  }
}
