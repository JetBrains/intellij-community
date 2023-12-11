// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JavaFocusModeProvider implements FocusModeProvider {
  @NotNull
  @Override
  public List<? extends Segment> calcFocusZones(@NotNull PsiFile file) {
    return SyntaxTraverser.psiTraverser(file)
      .postOrderDfsTraversal()
      .filter(e -> e instanceof PsiClass || e instanceof PsiMethod || e instanceof PsiClassInitializer)
      .filter(e -> {
        PsiElement parent = e.getParent();
        return parent instanceof PsiClass && !(parent instanceof PsiAnonymousClass);
      })
      .map(e -> e.getTextRange()).toList();
  }
}
