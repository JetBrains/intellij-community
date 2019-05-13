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
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightClassTypeReference extends LightClassReferenceBase implements PsiJavaCodeReferenceElement {

  private final @NotNull PsiClassType myType;

  private LightClassTypeReference(@NotNull PsiManager manager, @NotNull String text, @NotNull PsiClassType type) {
    super(manager, text);
    myType = type;
  }

  public LightClassTypeReference(@NotNull PsiManager manager, @NotNull PsiClassType type) {
    this(manager, type.getCanonicalText(true), type);
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myType.resolve();
  }

  @NotNull
  @Override
  public JavaResolveResult advancedResolve(boolean incompleteCode) {
    return myType.resolveGenerics();
  }

  @Nullable
  @Override
  public String getReferenceName() {
    return myType.getClassName();
  }

  @Override
  public PsiElement copy() {
    return new LightClassTypeReference(myManager, myText, myType);
  }

  @Override
  public boolean isValid() {
    return myType.isValid();
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myType.getResolveScope();
  }
}
