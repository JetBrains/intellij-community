/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.util.Range;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene Zhuravlev
*         Date: 10/25/13
*/
public abstract class SmartStepTarget {
  private final PsiElement myHighlightElement;
  private final String myLabel;
  private final boolean myNeedBreakpointRequest;
  private final Range<Integer> myExpressionLines;

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

  public Range<Integer> getCallingExpressionLines() {
    return myExpressionLines;
  }
}
