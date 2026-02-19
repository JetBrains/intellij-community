// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.localCanBeFinal;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

/**
 * Allows skipping 'Local variable or parameter can be final' highlighting for specific PsiVariables
 */
public interface IgnoreVariableCanBeFinalSupport {
  ExtensionPointName<IgnoreVariableCanBeFinalSupport> EP_NAME =
    ExtensionPointName.create("com.intellij.lang.jvm.ignoreVariableCanBeFinalSupport");

  /**
   * Checks if the given variable should be ignored for inspection.
   *
   * @param psiVariable the variable to check
   * @return true if the inspection should be skipped for the {@code psiVariable}, otherwise false
   */
  default boolean ignoreVariable(@NotNull PsiVariable psiVariable) {
    return false;
  }
}