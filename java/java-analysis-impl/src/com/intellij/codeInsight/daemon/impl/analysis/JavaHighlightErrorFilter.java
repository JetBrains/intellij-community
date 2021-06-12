// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class JavaHighlightErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    String description = element.getErrorDescription();
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiExpressionStatement && !PsiUtil.isStatement(parent)) {
        // unterminated expression statement which is not a statement at all:
        // let's report it as not-a-statement instead 
        // (see HighlightUtil.checkNotAStatement); it's more visible and provides 
        // more useful fixes.
        return false;
      }
    }
    return true;
  }
}
