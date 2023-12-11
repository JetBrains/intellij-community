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

import java.util.List;

public final class JavaMoveLeftRightHandler extends MoveElementLeftRightHandler {
  @Override
  public PsiElement @NotNull [] getMovableSubElements(@NotNull PsiElement element) {
    if (element instanceof PsiParameterList list) {
      return list.getParameters();
    }
    else if (element instanceof PsiExpressionList list) {
      return list.getExpressions();
    }
    else if (element instanceof PsiAnnotationParameterList list) {
      return list.getAttributes();
    }
    else if (element instanceof PsiArrayInitializerExpression arrayInitializer) {
      return arrayInitializer.getInitializers();
    }
    else if (element instanceof PsiArrayInitializerMemberValue arrayInitializerMemberValue) {
      return arrayInitializerMemberValue.getInitializers();
    }
    else if (element instanceof PsiClass cls && cls.isEnum()) {
      PsiEnumConstant[] enumConstants = PsiTreeUtil.getChildrenOfType(element, PsiEnumConstant.class);
      if (enumConstants != null) return enumConstants;
    }
    else if (element instanceof PsiReferenceList list) {
      return list.getReferenceElements();
    }
    else if (element instanceof PsiTypeElement) {
      PsiTypeElement[] result = PsiTreeUtil.getChildrenOfType(element, PsiTypeElement.class);
      if (result != null) return result;
    }
    else if (element instanceof PsiResourceList) {
      PsiElement[] result = PsiTreeUtil.getChildrenOfType(element, PsiResourceListElement.class);
      if (result != null) return result;
    }
    else if (element instanceof PsiPolyadicExpression list) {
      return list.getOperands();
    }
    else if (element instanceof PsiReferenceParameterList list) {
      return list.getTypeParameterElements();
    }
    else if (element instanceof PsiTypeParameterList list) {
      return list.getTypeParameters();
    }
    else if (element instanceof PsiModifierList) {
      final List<PsiElement> elements = PsiTreeUtil.getChildrenOfAnyType(element, PsiKeyword.class, PsiAnnotation.class);
      return elements.toArray(PsiElement.EMPTY_ARRAY);
    }
    else if (element instanceof PsiCaseLabelElementList list) {
      return list.getElements();
    }
    return PsiElement.EMPTY_ARRAY;
  }
}
