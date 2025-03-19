// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 */
public abstract class SmartStepTarget {
  private final PsiElement myHighlightElement;
  private final String myLabel;
  private final boolean myNeedBreakpointRequest;
  private Range<Integer> myExpressionLines;

  protected SmartStepTarget(@Nullable String label, @Nullable PsiElement highlightElement, boolean needBreakpointRequest, Range<Integer> expressionLines) {
    myHighlightElement = highlightElement;
    myLabel = label;
    myNeedBreakpointRequest = needBreakpointRequest;
    myExpressionLines = expressionLines;
  }

  public @Nullable PsiElement getHighlightElement() {
    return myHighlightElement;
  }

  public @Nullable @NlsSafe String getLabel() {
    return myLabel;
  }

  public boolean needsBreakpointRequest() {
    return myNeedBreakpointRequest;
  }

  public @Nullable Range<Integer> getCallingExpressionLines() {
    return myExpressionLines;
  }

  public void setCallingExpressionLines(Range<Integer> expressionLines) {
    myExpressionLines = expressionLines;
  }

  public @Nullable Icon getIcon() {
    return null;
  }

  public @Nullable String getClassName() {
    return null;
  }

  public @NotNull @NlsSafe String getPresentation() {
    return StringUtil.notNullize(getLabel());
  }

  @Override
  public String toString() {
    return getPresentation();
  }
}
