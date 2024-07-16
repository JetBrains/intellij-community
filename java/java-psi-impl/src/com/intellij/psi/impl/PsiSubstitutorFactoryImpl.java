// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public final class PsiSubstitutorFactoryImpl extends PsiSubstitutorFactory {
  @Override
  protected @NotNull PsiSubstitutor createSubstitutor(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    return new PsiSubstitutorImpl(typeParameter, mapping);
  }

  @Override
  protected @NotNull PsiSubstitutor createSubstitutor(@NotNull PsiClass aClass, PsiType[] mappings) {
    return new PsiSubstitutorImpl(aClass, mappings);
  }
  
  @Override
  protected @NotNull PsiSubstitutor createSubstitutor(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    return new PsiSubstitutorImpl(map);
  }
}
