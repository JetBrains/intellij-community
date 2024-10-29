// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@FunctionalInterface
interface ResultSink {
  void accept(@NotNull Object toolId,
              @NotNull PsiElement element,
              @NotNull List<? extends HighlightInfo> info);
}
