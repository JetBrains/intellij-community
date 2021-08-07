// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.JavaDummyHolder;
import org.jetbrains.annotations.NotNull;

final class BracePostFormatProcessor implements PostFormatProcessor {
  @NotNull
  @Override
  public PsiElement processElement(@NotNull PsiElement source, @NotNull CodeStyleSettings settings) {
    return isApplicable(source) ? new BraceEnforcer(settings).process(source) : source;
  }

  @NotNull
  @Override
  public TextRange processText(@NotNull PsiFile source, @NotNull TextRange rangeToReformat, @NotNull CodeStyleSettings settings) {
    return isApplicable(source) ? new BraceEnforcer(settings).processText(source, rangeToReformat) : rangeToReformat;
  }

  private static boolean isApplicable(@NotNull PsiElement source) {
    if (source.isValid()) {
      PsiFile file = source.getContainingFile();
      return file instanceof PsiJavaFile || file instanceof JavaDummyHolder;
    }
    return false;
  }
}
