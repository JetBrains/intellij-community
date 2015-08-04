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
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiReceiverParameterImpl extends CompositePsiElement implements PsiReceiverParameter {
  public PsiReceiverParameterImpl() {
    super(JavaElementType.RECEIVER_PARAMETER);
  }

  @NotNull
  public PsiThisExpression getIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiThisExpression.class);
  }

  @Nullable
  @Override
  public PsiModifierList getModifierList() {
    return PsiTreeUtil.getChildOfType(this, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @NotNull
  @Override
  public PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getIdentifier());
  }

  @NotNull
  @Override
  public PsiTypeElement getTypeElement() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiTypeElement.class);
  }

  @Nullable
  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Nullable
  @Override
  public PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot rename receiver parameter");
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException { }

  @Nullable
  @Override
  public Object computeConstantValue() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReceiverParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public int getTextOffset() {
    return getIdentifier().getTextOffset();
  }

  @Override
  public String toString() {
    return "PsiReceiverParameter";
  }
}
