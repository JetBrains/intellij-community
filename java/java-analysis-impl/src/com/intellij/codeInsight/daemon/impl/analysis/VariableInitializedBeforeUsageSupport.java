// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

/**
 * Allows skipping 'Variable might not have been initialized' highlighting for specific PsiReference
 */
public interface VariableInitializedBeforeUsageSupport {
  ExtensionPointName<VariableInitializedBeforeUsageSupport> EP_NAME =
    ExtensionPointName.create("com.intellij.lang.jvm.ignoreVariableInitializedBeforeUsageSupport");
  /**
   * Checks if the given expression should be ignored for inspection.
   *
   * @param psiExpression the expression to be checked for ignoring the initializer
   * @param psiVariable the variable from the expression resolving
   * @return true if the inspection should be skipped for the {@code psiExpression},
   *         otherwise false
   */
  default boolean ignoreVariableExpression(@NotNull PsiReferenceExpression psiExpression, @NotNull PsiVariable psiVariable) {
    return false;
  }
}