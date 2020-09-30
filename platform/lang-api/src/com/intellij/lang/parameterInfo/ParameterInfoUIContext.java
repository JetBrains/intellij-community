// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.parameterInfo;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;

import java.awt.*;

public interface ParameterInfoUIContext {
  String setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout,
                                      boolean isDisabledBeforeHighlight, Color background);
  void setupRawUIComponentPresentation(@NlsContexts.Label String htmlText);
  boolean isUIComponentEnabled();
  void setUIComponentEnabled(boolean enabled);

  int getCurrentParameterIndex();
  PsiElement getParameterOwner();

  boolean isSingleOverload();
  boolean isSingleParameterInfo();
  Color getDefaultParameterColor();
}
