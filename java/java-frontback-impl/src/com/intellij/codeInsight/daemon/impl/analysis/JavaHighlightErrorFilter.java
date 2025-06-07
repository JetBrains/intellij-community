// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.highlighting.HighlightErrorFilter;
import com.intellij.idea.AppMode;
import com.intellij.java.codeserver.highlighting.JavaSyntaxErrorChecker;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiErrorElement;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

public final class JavaHighlightErrorFilter extends HighlightErrorFilter {
  @Override
  public boolean shouldHighlightErrorElement(@NotNull PsiErrorElement element) {
    // todo we should handle it on the platform side IJPL-185339
    // should be skipped on the client only, because the client doesn't know about fixes
    if (element.getLanguage().equals(JavaLanguage.INSTANCE) && PlatformUtils.isJetBrainsClient()) {
      return false;
    }
    return JavaSyntaxErrorChecker.shouldHighlightErrorElement(element);
  }
}
