// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.presentation.java.ClassPresentationUtil;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Internal
public final class PsiClassRenderingInfo implements PsiElementRenderingInfo<PsiClass> {

  public static final PsiElementRenderingInfo<PsiClass> INSTANCE = new PsiClassRenderingInfo();

  private PsiClassRenderingInfo() { }

  @Override
  public @NotNull String getPresentableText(@NotNull PsiClass element) {
    return ClassPresentationUtil.getNameForClass(element, false);
  }

  @Override
  public @Nullable String getContainerText(@NotNull PsiClass element) {
    return getContainerTextStatic(element);
  }

  @Nullable
  public static String getContainerTextStatic(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file instanceof PsiClassOwner) {
      String packageName = ((PsiClassOwner)file).getPackageName();
      if (packageName.isEmpty()) return null;
      return "(" + packageName + ")";
    }
    return null;
  }
}
