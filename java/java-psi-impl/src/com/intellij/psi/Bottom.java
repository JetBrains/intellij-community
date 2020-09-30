// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Used in Generify refactoring
 */
public final class Bottom extends PsiType {
  public static final Bottom BOTTOM = new Bottom();

  private Bottom() {
    super(TypeAnnotationProvider.EMPTY);
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return getCanonicalText();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return "_";
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean equalsToText(@NotNull String text) {
    return text.equals("_");
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Bottom;
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
  public PsiType @NotNull [] getSuperTypes() {
    throw new UnsupportedOperationException();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }
}