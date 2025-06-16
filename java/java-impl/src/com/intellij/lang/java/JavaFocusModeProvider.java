// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public final class JavaFocusModeProvider implements FocusModeProvider {
  @Override
  public @Unmodifiable @NotNull List<? extends Segment> calcFocusZones(@NotNull PsiFile psiFile) {
    return SyntaxTraverser.psiTraverser(psiFile)
      .postOrderDfsTraversal()
      .filter(e -> e instanceof PsiClass || e instanceof PsiMethod || e instanceof PsiClassInitializer)
      .filter(e -> {
        PsiElement parent = e.getParent();
        return parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass);
      })
      .map(e -> e.getTextRange()).toList();
  }
}
