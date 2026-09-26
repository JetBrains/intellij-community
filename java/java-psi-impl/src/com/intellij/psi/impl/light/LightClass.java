// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.light;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class LightClass extends AbstractLightClass {
  private final PsiClass myDelegate;

  public LightClass(@NotNull PsiClass delegate) {
    this(delegate, JavaLanguage.INSTANCE);
  }

  public LightClass(@NotNull PsiClass delegate, final Language language) {
    super(delegate.getManager(), language);
    myDelegate = delegate;
  }

  @Override
  public @NotNull PsiClass getDelegate() {
    return myDelegate;
  }

  @Override
  public @NotNull PsiElement copy() {
    return new LightClass(this);
  }

}
