// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class FilterPositionUtil {
  @Nullable
  public static PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
    return element == null ? null : PsiTreeUtil.prevCodeLeaf(element);
  }
}