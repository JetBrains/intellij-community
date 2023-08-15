// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.annotation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ContributedReferencesAnnotator {
  void annotate(@NotNull PsiElement element,
                @NotNull List<PsiReference> references,
                @NotNull AnnotationHolder holder);
}
