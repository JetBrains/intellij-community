// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.parameterInfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;


public interface ParameterInfoUIContext {
  String setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout,
                                      boolean isDisabledBeforeHighlight, Color background);
  void setupRawUIComponentPresentation(@NlsContexts.Label String htmlText);

  @ApiStatus.Internal
  default void setupSignatureHtmlPresentation(@NotNull List<@NotNull ParameterHtmlPresentation> parameters,
                                              int currentParameterIndex, @NotNull String separator, boolean isDeprecated) {
    @NlsSafe
    StringBuilder sb = new StringBuilder();
    boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (isDeprecated) sb.append("<strike>");
    for (int i = 0; i < parameters.size(); i++) {
      if (i > 0) sb.append(separator).append(" ");
      ParameterHtmlPresentation parameter = parameters.get(i);
      if (isUnitTestMode && parameter.isMismatched()) sb.append("<mismatched>");
      String defaultValue = parameter.defaultValue != null ? parameter.defaultValue : "";
      if (i == currentParameterIndex) {
        sb.append("<b>").append(parameter.nameAndType).append(defaultValue).append("</b>");
      } else {
        sb.append(parameter.nameAndType).append(defaultValue);
      }
      if (isUnitTestMode && parameter.isMismatched()) sb.append("</mismatched>");
    }
    if (isDeprecated) sb.append("</strike>");
    setupRawUIComponentPresentation(sb.toString());
  }

  boolean isUIComponentEnabled();
  void setUIComponentEnabled(boolean enabled);

  default void setUIComponentVisible(boolean visible) {}
  default boolean isUIComponentVisible() { return true; }

  int getCurrentParameterIndex();
  PsiElement getParameterOwner();

  boolean isSingleOverload();
  boolean isSingleParameterInfo();
  Color getDefaultParameterColor();

  @ApiStatus.Internal
  record ParameterHtmlPresentation(@NotNull String nameAndType, @Nullable String defaultValue, boolean isMismatched) {
  }
}
