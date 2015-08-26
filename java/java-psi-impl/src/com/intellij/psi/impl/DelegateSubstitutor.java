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
package com.intellij.psi.impl;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DelegateSubstitutor implements PsiSubstitutor {

  private final @NotNull PsiSubstitutor myDelegate;

  public DelegateSubstitutor(@NotNull PsiSubstitutor delegate) {
    myDelegate = delegate;
  }

  @Override
  @Nullable
  public PsiType substitute(@NotNull PsiTypeParameter typeParameter) {
    return myDelegate.substitute(typeParameter);
  }

  @Override
  public PsiType substitute(@Nullable PsiType type) {
    return myDelegate.substitute(type);
  }

  @Override
  public PsiType substituteWithBoundsPromotion(@NotNull PsiTypeParameter typeParameter) {
    return myDelegate.substituteWithBoundsPromotion(typeParameter);
  }

  @Override
  @NotNull
  public PsiSubstitutor put(@NotNull PsiTypeParameter classParameter, PsiType mapping) {
    return myDelegate.put(classParameter, mapping);
  }

  @Override
  @NotNull
  public PsiSubstitutor putAll(@NotNull PsiClass parentClass, PsiType[] mappings) {
    return myDelegate.putAll(parentClass, mappings);
  }

  @Override
  @NotNull
  public PsiSubstitutor putAll(@NotNull PsiSubstitutor another) {
    return myDelegate.putAll(another);
  }

  @Override
  @NotNull
  public Map<PsiTypeParameter, PsiType> getSubstitutionMap() {
    return myDelegate.getSubstitutionMap();
  }

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public void ensureValid() {
    myDelegate.ensureValid();
  }
}
