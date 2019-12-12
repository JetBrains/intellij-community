// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.*;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

@ApiStatus.Internal
public class PsiSubstitutorFactoryImpl extends PsiSubstitutorFactory {
  @Override
  protected PsiSubstitutor createSubstitutor(PsiTypeParameter typeParameter, PsiType mapping) {
    return new PsiSubstitutorImpl(typeParameter, mapping);
  }

  @Override
  protected PsiSubstitutor createSubstitutor(PsiClass aClass, PsiType[] mappings) {
    return new PsiSubstitutorImpl(aClass, mappings);
  }

  @Override
  protected PsiSubstitutor createSubstitutor(Map<PsiTypeParameter, PsiType> map) {
    return new PsiSubstitutorImpl(map);
  }
}
