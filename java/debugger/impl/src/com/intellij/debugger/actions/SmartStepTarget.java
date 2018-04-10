/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.actions;

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
  public String getPresentation() {
    return StringUtil.notNullize(getLabel());
  }
}
