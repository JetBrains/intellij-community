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

  @NotNull
  @Override
  public PsiParameter getPrototype() {
    return myPrototype;
  }

  @Override
  @NotNull
  public PsiType getType() {
    return mySubstitutor.substitute(myPrototype.getType());
  }

  @Override
  @Nullable
  public PsiModifierList getModifierList() {
    return myPrototype.getModifierList();
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NonNls @NotNull String name) {
    return myPrototype.hasModifierProperty(name);
  }

  @Override
  @NotNull
  public PsiElement getDeclarationScope() {
    return myPrototype.getDeclarationScope();
  }

  @Override
  @Nullable
  public PsiExpression getInitializer() {
    return myPrototype.getInitializer();
  }

  @Override
  public boolean isVarArgs() {
    return myPrototype.isVarArgs();
  }

  @Override
  @Nullable
  public PsiTypeElement getTypeElement() {
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
  @Nullable
  public Object computeConstantValue() {
    return myPrototype.computeConstantValue();
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
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
