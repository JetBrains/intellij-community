package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HighlightInfoFilter {
  HighlightInfoFilter[] EMPTY_ARRAY = new HighlightInfoFilter[0];
  ExtensionPointName<HighlightInfoFilter> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.daemon.highlightInfoFilter");

  /**
   * @param file - might (and will be) null. Return true in this case if you'd like to switch this kind of highlighting in ANY file
   */
  boolean accept(@NotNull HighlightInfo highlightInfo, @Nullable PsiFile file);
}

