// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Nullable
  public PsiElement getHighlightElement() {
    return myHighlightElement;
  }

  @Nullable
  @NlsSafe
  public String getLabel() {
    return myLabel;
  }

  public boolean needsBreakpointRequest() {
    return myNeedBreakpointRequest;
  }

  @Nullable
  public Range<Integer> getCallingExpressionLines() {
    return myExpressionLines;
  }

  public void setCallingExpressionLines(Range<Integer> expressionLines) {
    myExpressionLines = expressionLines;
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  @NotNull
  @NlsSafe
  public String getPresentation() {
    return StringUtil.notNullize(getLabel());
  }

  @Override
  public String toString() {
    return getPresentation();
  }
}
