// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class LocalInspectionToolSession extends UserDataHolderBase {
  private final PsiFile myFile;
  private final TextRange myPriorityRange;
  private final TextRange myRestrictRange;

  LocalInspectionToolSession(@NotNull PsiFile file, @NotNull TextRange priorityRange, @NotNull TextRange restrictRange) {
    myFile = file;
    myPriorityRange = priorityRange;
    myRestrictRange = restrictRange;
  }

  public @NotNull PsiFile getFile() {
    return myFile;
  }

  /**
   * @return range (inside the {@link #getFile()}) which the current session will try to highlight first.
   * Usually it corresponds to the visible view port in the editor.
   */
  public @NotNull TextRange getPriorityRange() {
    return myPriorityRange;
  }

  /**
   * @return range (inside the {@link #getFile()}) which the current session will restrict itself to.
   */
  public @NotNull TextRange getRestrictRange() {
    return myRestrictRange;
  }
}
