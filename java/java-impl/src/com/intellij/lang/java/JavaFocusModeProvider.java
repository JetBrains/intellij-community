// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.codeInsight.daemon.impl.focusMode.FocusModeProvider;
import com.intellij.openapi.util.Segment;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JavaFocusModeProvider implements FocusModeProvider {
  @NotNull
  @Override
  public List<? extends Segment> calcFocusZones(@NotNull PsiFile file) {
    return SyntaxTraverser.psiTraverser(file)
      .postOrderDfsTraversal()
      .filter(e -> e instanceof PsiClass || e instanceof PsiMethod)
      .map(e -> e.getTextRange()).toList();
  }
}
