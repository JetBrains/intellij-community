/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.moveLeftRight;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaMoveLeftRightHandler extends MoveStatementLeftRightHandler {
  @Nullable
  @Override
  public PsiElement[] getElementListInContext(@NotNull PsiElement element) {
    while (element != null) {
      if (element instanceof PsiParameterList) {
        return ((PsiParameterList)element).getParameters();
      }
      else if (element instanceof PsiExpressionList) {
        PsiExpression[] expressions = ((PsiExpressionList)element).getExpressions();
        if (expressions.length > 1) return expressions;
      }
      else if (element instanceof PsiArrayInitializerExpression) {
        PsiExpression[] expressions = ((PsiArrayInitializerExpression)element).getInitializers();
        if (expressions.length > 1) return expressions;
      }
      else if (element instanceof PsiClass && ((PsiClass)element).isEnum()) {
        return PsiTreeUtil.getChildrenOfType(element, PsiEnumConstant.class);
      }
      element = element.getParent();
    }
    return null;
  }
}
