// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated all methods are deprecated
 */
@Deprecated
public final class HighlightUtil {

  private HighlightUtil() { }

  /**
   * @deprecated use {@link HighlightNamesUtil#formatClass(PsiClass)} or 
   * preferably {@link PsiFormatUtil#formatClass(PsiClass, int)} directly  
   */
  @Deprecated
  public static @NotNull @NlsSafe String formatClass(@NotNull PsiClass aClass) {
    return HighlightNamesUtil.formatClass(aClass);
  }

  /**
   * @deprecated use {@link HighlightNamesUtil#formatClass(PsiClass)} or 
   * preferably {@link PsiFormatUtil#formatClass(PsiClass, int)} directly  
   */
  @Deprecated
  public static @NotNull String formatClass(@NotNull PsiClass aClass, boolean fqn) {
    return HighlightNamesUtil.formatClass(aClass, fqn);
  }
}
