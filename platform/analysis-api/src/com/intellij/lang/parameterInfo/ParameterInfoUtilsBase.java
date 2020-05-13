// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParameterInfoUtilsBase {

  @Nullable
  public static <T extends PsiElement> T findParentOfTypeWithStopElements(PsiFile file,
                                                                          int offset,
                                                                          Class<T> parentClass,
                                                                          Class<? extends PsiElement> @NotNull ... stopAt) {
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;

    T parentOfType = PsiTreeUtil.getParentOfType(element, parentClass, true, stopAt);
    if (element instanceof PsiWhiteSpace) {
      parentOfType = PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(element), parentClass, true, stopAt);
    }
    return parentOfType;
  }

  @Nullable
  public static <T extends PsiElement> T findParentOfType(PsiFile file, int offset, Class<T> parentClass) {
    return findParentOfTypeWithStopElements(file, offset, parentClass);
  }
}
