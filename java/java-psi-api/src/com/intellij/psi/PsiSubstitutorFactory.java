// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@ApiStatus.Internal
public abstract class PsiSubstitutorFactory {
  protected abstract @NotNull PsiSubstitutor createSubstitutor(@NotNull PsiTypeParameter typeParameter, PsiType mapping);

  protected abstract @NotNull PsiSubstitutor createSubstitutor(@NotNull PsiClass aClass, PsiType[] mappings);

  protected abstract @NotNull PsiSubstitutor createSubstitutor(@NotNull Map<? extends PsiTypeParameter, ? extends PsiType> map);

  static PsiSubstitutorFactory getInstance() {
    return ServiceManager.getService(PsiSubstitutorFactory.class);
  }
}
