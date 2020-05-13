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
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * A type which represents an omitted type for parameter of lambda expression.
 */
public class PsiLambdaParameterType extends PsiType {
  private final PsiParameter myParameter;

  public PsiLambdaParameterType(@NotNull PsiParameter parameter) {
    super(TypeAnnotationProvider.EMPTY);
    myParameter = parameter;
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return getCanonicalText();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "<lambda parameter>";
  }

  @Override
  public boolean isValid() {
    return myParameter.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @Override
  public PsiType @NotNull [] getSuperTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiParameter getParameter() {
    return myParameter;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof PsiLambdaParameterType && myParameter.equals(((PsiLambdaParameterType)o).myParameter);
  }

  @Override
  public int hashCode() {
    return myParameter.hashCode();
  }
}