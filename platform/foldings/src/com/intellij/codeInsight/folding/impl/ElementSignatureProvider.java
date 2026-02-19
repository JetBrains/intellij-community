// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public interface ElementSignatureProvider {

  @Nullable
  String getSignature(@NotNull PsiElement element);

  @Nullable
  PsiElement restoreBySignature(@NotNull PsiFile file, @NotNull String signature, @Nullable StringBuilder processingInfoStorage);
}
