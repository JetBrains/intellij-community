// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.java.codeserver.highlighting.JavaSyntaxErrorChecker;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

public final class JavaHighlightErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    return JavaSyntaxErrorChecker.shouldHighlightErrorElement(element);
  }
}
