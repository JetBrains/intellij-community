// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightParameterWrapper extends LightElement implements PsiParameter, PsiMirrorElement {

  private final @NotNull PsiParameter myPrototype;
  private final @NotNull PsiSubstitutor mySubstitutor;

  public LightParameterWrapper(@NotNull PsiParameter prototype, @NotNull PsiSubstitutor substitutor) {
    super(prototype.getManager(), prototype.getLanguage());
    myPrototype = prototype;
    mySubstitutor = substitutor;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }
  @Override
  public @NotNull PsiParameter getPrototype() {
    return myPrototype;
  }

  @Override
  public @NotNull String getName() {
    return myPrototype.getName();
  }

  @Override
  public @NotNull PsiType getType() {
    return mySubstitutor.substitute(myPrototype.getType());
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return myPrototype.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @NotNull String name) {
    return myPrototype.hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiElement getDeclarationScope() {
    return myPrototype.getDeclarationScope();
  }

  @Override
  public @Nullable PsiExpression getInitializer() {
    return myPrototype.getInitializer();
  }

  @Override
  public boolean isVarArgs() {
    return myPrototype.isVarArgs();
  }

  @Override
  public @Nullable PsiTypeElement getTypeElement() {
    return myPrototype.getTypeElement();
  }

  @Override
  public boolean hasInitializer() {
    return myPrototype.hasInitializer();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @Nullable Object computeConstantValue() {
    return myPrototype.computeConstantValue();
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
    return myPrototype.getNameIdentifier();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public String toString() {
    return "List PSI parameter wrapper: " + myPrototype;
  }
}
