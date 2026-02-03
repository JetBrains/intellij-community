// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.infos;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;

public class ClassCandidateInfo extends CandidateInfo {
  public ClassCandidateInfo(@NotNull PsiElement candidate,
                            @NotNull PsiSubstitutor substitutor,
                            boolean accessProblem,
                            PsiElement currFileContext) {
    super(candidate, substitutor, accessProblem, false, currFileContext);
  }

  public ClassCandidateInfo(@NotNull PsiElement candidate, @NotNull PsiSubstitutor substitutor) {
    super(candidate, substitutor, false, false);
  }

  @Override
  public @NotNull PsiClass getElement() {
    return (PsiClass)super.getElement();
  }
}