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
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiTypeParameterListOwner;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightTypeParameterBuilder extends LightPsiClassBuilder implements PsiTypeParameter {

  private final PsiTypeParameterListOwner myOwner;
  private final int myIndex;

  public LightTypeParameterBuilder(@NotNull String name, PsiTypeParameterListOwner owner, int index) {
    super(owner, name);
    myOwner = owner;
    myIndex = index;
  }

  @Nullable
  @Override
  public PsiTypeParameterListOwner getOwner() {
    return myOwner;
  }

  @Override
  public int getIndex() {
    return myIndex;
  }

  @NotNull
  @Override
  public PsiAnnotation[] getAnnotations() {
    return getModifierList().getAnnotations();
  }

  @NotNull
  @Override
  public PsiAnnotation[] getApplicableAnnotations() {
    return getModifierList().getApplicableAnnotations();
  }

  @Nullable
  @Override
  public PsiAnnotation findAnnotation(@NotNull @NonNls String qualifiedName) {
    return getModifierList().findAnnotation(qualifiedName);
  }

  @NotNull
  @Override
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    return getModifierList().addAnnotation(qualifiedName);
  }
}
