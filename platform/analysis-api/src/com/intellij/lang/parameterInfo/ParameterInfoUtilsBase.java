// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ParameterInfoUtilsBase {

  public static @Nullable <T extends PsiElement> T findParentOfTypeWithStopElements(PsiFile file,
                                                                                    int offset,
                                                                                    Class<T> parentClass,
                                                                                    Class<? extends PsiElement> @NotNull ... stopAt) {
    PsiElement element = file.findElementAt(offset);
    return findParentOfTypeWithStopElements(element, parentClass, stopAt);
  }

  public static @Nullable <T extends PsiElement> T findParentOfTypeWithStopElements(PsiElement start, Class<T> parentClass,
                                                                                    Class<? extends PsiElement> @NotNull ... stopAt) {
    if (start == null) return null;

    T parentOfType = PsiTreeUtil.getParentOfType(start, parentClass, true, stopAt);
    if (start instanceof PsiWhiteSpace) {
      parentOfType = PsiTreeUtil.getParentOfType(PsiTreeUtil.prevLeaf(start), parentClass, true, stopAt);
    }
    return parentOfType;
  }

  public static @Nullable <T extends PsiElement> T findParentOfType(PsiFile file, int offset, Class<T> parentClass) {
    return findParentOfTypeWithStopElements(file, offset, parentClass);
  }
}
