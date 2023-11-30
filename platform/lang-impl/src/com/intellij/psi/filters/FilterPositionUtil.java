// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class FilterPositionUtil {
  public static @Nullable PsiElement searchNonSpaceNonCommentBack(PsiElement element) {
    return element == null ? null : PsiTreeUtil.prevCodeLeaf(element);
  }
}