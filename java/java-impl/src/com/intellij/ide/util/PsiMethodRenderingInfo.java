// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtil.FormatMethodOptions;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@Internal
public final class PsiMethodRenderingInfo implements PsiElementCellRenderingInfo<PsiMethod> {

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
  public Icon getIcon(PsiElement element) {
    return PsiElementCellRenderingInfo.super.getIcon(myShowMethodNames ? element : fetchContainer((PsiMethod)element));
  }

  @Override
  public String getElementText(PsiMethod element) {
    final PsiNamedElement container = fetchContainer(element);
    String text = container instanceof PsiClass ? PsiClassRenderingInfo.INSTANCE.getElementText((PsiClass)container) : container.getName();
    if (myShowMethodNames) {
      text += "." + PsiFormatUtil.formatMethod(element, PsiSubstitutor.EMPTY, myOptions, PsiFormatUtilBase.SHOW_TYPE);
    }
    return text;
  }

  @Override
  public String getContainerText(PsiMethod element, String name) {
    return PsiClassRenderingInfo.getContainerTextStatic(element);
  }

  private static PsiNamedElement fetchContainer(@NotNull PsiMethod element) {
    PsiClass aClass = element.getContainingClass();
    return aClass == null ? element.getContainingFile() : aClass;
  }
}
