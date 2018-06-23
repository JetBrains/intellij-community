// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.hint;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface ImplementationTextProcessor {
  String process(@NotNull String text, @NotNull PsiElement element);
}