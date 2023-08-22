// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LightParameter extends LightVariableBuilder<LightVariableBuilder<?>> implements PsiParameter {
  private final PsiElement myDeclarationScope;
  private final boolean myVarArgs;

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope) {
    this(name, type, declarationScope, declarationScope.getLanguage());
  }

  public LightParameter(@NonNls @NotNull String name, @NotNull PsiType type, @NotNull PsiElement declarationScope, @NotNull Language language) {
    this(name, type, declarationScope, language, type instanceof PsiEllipsisType);
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

  @Override
  public @NotNull PsiElement getDeclarationScope() {
    return myDeclarationScope;
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
  public String toString() {
    return "Light Parameter";
  }

  @Override
  public boolean isVarArgs() {
    return myVarArgs;
  }
}
