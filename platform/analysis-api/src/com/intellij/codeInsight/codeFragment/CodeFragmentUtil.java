// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeFragment;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public final class CodeFragmentUtil {
  public static Position getPosition(final @NotNull PsiElement element, final int startOffset, final int endOffset) {
    final int offset = element.getTextOffset();
    if (offset < startOffset) {
      return Position.BEFORE;
    }
    if (element.getTextOffset() < endOffset) {
      return Position.INSIDE;
    }
    return Position.AFTER;
  }

  public static boolean elementFit(final PsiElement element, final int start, final int end) {
    return element != null && start <= element.getTextOffset() && element.getTextOffset() + element.getTextLength() <= end;
  }
}
