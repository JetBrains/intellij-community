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
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class LightParameter extends LightVariableBuilder<LightVariableBuilder> implements PsiParameter {
  private final PsiElement myDeclarationScope;
  private final boolean myVarArgs;

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope) {
    this(name, type, declarationScope, declarationScope.getLanguage());
  }

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope, @NotNull Language language) {
    this(name, type, declarationScope, language, type instanceof PsiEllipsisType);
  }

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope,
                        @NotNull Language language, @NotNull LightModifierList modifierList) {
    this(name, type, declarationScope, language, modifierList, type instanceof PsiEllipsisType);
  }

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope, @NotNull Language language, boolean isVarArgs) {
    this(name, type, declarationScope, language, new LightModifierList(declarationScope.getManager()), isVarArgs);
  }

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope, @NotNull Language language,
                        @NotNull LightModifierList modifierList, boolean isVarArgs) {
    super(declarationScope.getManager(), name, type, language, modifierList);
    myDeclarationScope = declarationScope;
    myVarArgs = isVarArgs;
  }

  @NotNull
  @Override
  public PsiElement getDeclarationScope() {
    return myDeclarationScope;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParameter(this);
    }
  }

  @Override
  public String toString() {
    return "Light Parameter";
  }

  @Override
  public boolean isVarArgs() {
    return myVarArgs;
  }
}
