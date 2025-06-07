// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.util;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodSignatureHandMade extends MethodSignatureBase {
  private final String myName;
  private final boolean myIsConstructor;

  MethodSignatureHandMade(@NotNull String name,
                          @Nullable PsiParameterList parameterList,
                          @Nullable PsiTypeParameterList typeParameterList,
                          @NotNull PsiSubstitutor substitutor,
                          boolean isConstructor) {
    super(substitutor, parameterList, typeParameterList);
    myName = name;
    myIsConstructor = isConstructor;
  }

  MethodSignatureHandMade(@NotNull String name,
                          PsiType @NotNull [] parameterTypes,
                          PsiTypeParameter @NotNull [] typeParameters,
                          @NotNull PsiSubstitutor substitutor,
                          boolean isConstructor) {
    super(substitutor, parameterTypes, typeParameters);
    myName = name;
    myIsConstructor = isConstructor;
  }


  @Override
  public @NotNull String getName() {
    return myName;
  }

  @Override
  public boolean isRaw() {
    for (final PsiTypeParameter typeParameter : myTypeParameters) {
      if (getSubstitutor().substitute(typeParameter) == null) return true;
    }
    return false;
  }

  @Override
  public boolean isConstructor() {
    return myIsConstructor;
  }
}
