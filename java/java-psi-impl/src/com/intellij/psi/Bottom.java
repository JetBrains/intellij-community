/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
 * Used in Generify refactoring
 */
public class Bottom extends PsiType {
  public static final Bottom BOTTOM = new Bottom();

  private Bottom() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return "_";
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "_";
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.equals("_");
  }

  public boolean equals(Object o) {
    if (o instanceof Bottom) {
      return true;
    }

    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    if (visitor instanceof PsiTypeVisitorEx) {
      return ((PsiTypeVisitorEx<A>)visitor).visitBottom(this);
    }
    else {
      return visitor.visitType(this);
    }
  }

  @Override
  @NotNull
  public PsiType[] getSuperTypes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }
}
