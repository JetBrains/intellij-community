// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

@ApiStatus.Experimental
public interface ExceptionAnalysisProvider {
  /**
   * @param anchor a place (leaf element) in the source code where exception occurred
   * @param info exception info
   * @param nextFrames known subsequent stack frames
   * @return an action to provide additional analysis for given exception; null if not available.
   */
  @Nullable AnAction getAnalysisAction(@NotNull PsiElement anchor,
                                       @NotNull ExceptionInfo info,
                                       @NotNull Supplier<List<StackLine>> nextFrames);

  /**
   * @param anchor a place (method name identifier) in the source code where next stack frame row is invoked 
   * @param nextFrames known subsequent stack frames
   * @return an action to provide additional analysis for given location; null if not available
   */
  @Nullable AnAction getIntermediateRowAnalysisAction(@NotNull PsiElement anchor,
                                                      @NotNull Supplier<List<StackLine>> nextFrames);

  /**
   * Stack frame descriptor
   */
  class StackLine {
    private final String myClassName;
    private final String myMethodName;

    public StackLine(String className, String methodName) {
      myClassName = className;
      myMethodName = methodName;
    }

    /**
     * @return fully-qualified name, as presented in the stack trace
     */
    public String getClassName() {
      return myClassName;
    }

    /**
     * @return method name
     */
    public String getMethodName() {
      return myMethodName;
    }
  }
}
