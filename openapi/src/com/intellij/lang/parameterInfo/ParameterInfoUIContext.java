package com.intellij.lang.parameterInfo;

import com.intellij.psi.PsiElement;

import java.awt.*;

public interface ParameterInfoUIContext {
  void setupUIComponentPresentation(String text, int highlightStartOffset, int highlightEndOffset, boolean isDisabled, boolean strikeout,
                                    boolean isDisabledBeforeHighlight, Color background);
  boolean isUIComponentEnabled();
  void setUIComponentEnabled(boolean enabled);

  int getCurrentParameterIndex();
  PsiElement getParameterOwner();

  Color getDefaultParameterColor();
}
