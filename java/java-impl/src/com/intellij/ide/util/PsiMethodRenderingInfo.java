// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtil.FormatMethodOptions;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

@Internal
public final class PsiMethodRenderingInfo implements PsiElementRenderingInfo<PsiMethod> {

  private final boolean myShowMethodNames;
  @FormatMethodOptions
  private final int myOptions;

  public PsiMethodRenderingInfo(boolean showMethodNames) {
    this(showMethodNames, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS);
  }

  public PsiMethodRenderingInfo(boolean showMethodNames, @FormatMethodOptions int options) {
    myShowMethodNames = showMethodNames;
    myOptions = options;
  }

  @Override
  public @Nullable Icon getIcon(@NotNull PsiMethod element) {
    return myShowMethodNames
           ? PsiElementRenderingInfo.super.getIcon(element)
           : fetchContainer(element).getIcon(0);
  }

  @Override
  public @NotNull String getPresentableText(@NotNull PsiMethod element) {
    final PsiNamedElement container = fetchContainer(element);
    String text = container instanceof PsiClass
                  ? PsiClassRenderingInfo.INSTANCE.getPresentableText((PsiClass)container)
                  : container.getName();
    if (myShowMethodNames) {
      text += "." + PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY, myOptions, PsiFormatUtilBase.SHOW_TYPE);
    }
    return text;
  }

  @Override
  public @Nullable String getContainerText(@NotNull PsiMethod element) {
    return PsiClassRenderingInfo.getContainerTextStatic(element);
  }

  private static PsiNamedElement fetchContainer(@NotNull PsiMethod element) {
    PsiClass aClass = element.getContainingClass();
    return aClass == null ? element.getContainingFile() : aClass;
  }
}
