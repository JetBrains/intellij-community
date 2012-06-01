/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiReferenceExpressionBase extends ExpressionPsiElement implements PsiReferenceExpression {
  public PsiReferenceExpressionBase(@NotNull final IElementType type) {
    super(type);
  }

  @Override
  public PsiElement bindToElementViaStaticImport(@NotNull final PsiClass qualifierClass) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void setQualifierExpression(@Nullable PsiExpression newQualifier) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement getElement() {
    return this;
  }

  @Override
  public PsiElement resolve() {
    return advancedResolve(false).getElement();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public boolean isReferenceTo(final PsiElement element) {
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    // this reference's variants are rather obtained with processVariants()
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public PsiReference getReference() {
    return this;
  }

  @NotNull
  @Override
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    final JavaResolveResult[] results = multiResolve(incompleteCode);
    return results.length == 1 ? results[0] : JavaResolveResult.EMPTY;
  }

  @Override
  public String getReferenceName() {
    final PsiElement element = getReferenceNameElement();
    return element != null ? element.getText() : null;
  }

  @Override
  public PsiReferenceParameterList getParameterList() {
    return PsiTreeUtil.getChildOfType(this, PsiReferenceParameterList.class);
  }

  @NotNull
  @Override
  public PsiType[] getTypeParameters() {
    final PsiReferenceParameterList parameterList = getParameterList();
    return parameterList != null ? parameterList.getTypeArguments() : PsiType.EMPTY_ARRAY;
  }

  @Override
  public boolean isQualified() {
    return getQualifier() != null;
  }

  @Override
  public String getQualifiedName() {
    return getCanonicalText();
  }
}
