// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static java.util.Collections.singletonList;

public abstract class PsiLanguageInjectionHostVisitor extends PsiElementVisitor implements HintedPsiElementVisitor {
  @Override
  public @NotNull List<Class<?>> getHintPsiElements() {
    return singletonList(PsiLanguageInjectionHost.class);
  }
}
