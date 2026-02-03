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
                                       @NotNull Supplier<? extends List<StackLine>> nextFrames);

  /**
   * @param anchor a place (method name identifier) in the source code where next stack frame row is invoked 
   * @param nextFrames known subsequent stack frames
   * @return an action to provide additional analysis for given location; null if not available
   */
  @Nullable AnAction getIntermediateRowAnalysisAction(@NotNull PsiElement anchor,
                                                      @NotNull Supplier<? extends List<StackLine>> nextFrames);

  /**
   * Stack frame descriptor
   */
  class StackLine {
    private final @NotNull String myClassName;
    private final @NotNull String myMethodName;
    private final @Nullable String myFileName;

    public StackLine(@NotNull String className, @NotNull String methodName, @Nullable String fileName) {
      myClassName = className;
      myMethodName = methodName;
      myFileName = fileName;
    }

    /**
     * @return fully-qualified name, as presented in the stack trace
     */
    public @NotNull String getClassName() {
      return myClassName;
    }

    /**
     * @return method name
     */
    public @NotNull String getMethodName() {
      return myMethodName;
    }

    /**
     * @return file name
     */
    public @Nullable String getFileName() {
      return myFileName;
    }
  }
}
