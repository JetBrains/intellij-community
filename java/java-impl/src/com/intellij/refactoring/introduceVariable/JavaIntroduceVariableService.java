// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.introduceVariable;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@ApiStatus.Internal
public class JavaIntroduceVariableService {
  public static @NotNull JavaIntroduceVariableService getInstance() {
    return ApplicationManager.getApplication().getService(JavaIntroduceVariableService.class);
  }

  /**
   * Ranges of occurrences of an expression in an enclosing scope.
   */
  @NotNull
  public List<@NotNull TextRange> getOccurrences(@NotNull PsiExpression expression) {
    return List.of(expression.getTextRange());
  }

  /**
   * Introduce variable for expression.
   * @return created variable or null if the refactoring cannot be performed
   */
  public @Nullable PsiVariable introduceVariable(@NotNull PsiExpression expression, boolean replaceAll) {
    return null;
  }
}
