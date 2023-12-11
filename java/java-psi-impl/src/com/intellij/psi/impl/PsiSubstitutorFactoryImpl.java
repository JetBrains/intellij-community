// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public final class PsiSubstitutorFactoryImpl extends PsiSubstitutorFactory {
  @NotNull
  @Override
  protected PsiSubstitutor createSubstitutor(@NotNull PsiTypeParameter typeParameter, PsiType mapping) {
    return new PsiSubstitutorImpl(typeParameter, mapping);
  }

  @NotNull
  @Override
  protected PsiSubstitutor createSubstitutor(@NotNull PsiClass aClass, PsiType[] mappings) {
    return new PsiSubstitutorImpl(aClass, mappings);
  }
  
  @NotNull
  @Override
  protected PsiSubstitutor createSubstitutor(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map) {
    return new PsiSubstitutorImpl(map);
  }
}
