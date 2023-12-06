// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;


/**
 * This interface provides support for checking whether a method call expression should be ignored
 * for inspection that checks for potential null pointer exceptions (NPE).
 */
public interface MethodCallProduceNPESupport {
  ExtensionPointName<MethodCallProduceNPESupport> EP_NAME =
    ExtensionPointName.create("com.intellij.lang.jvm.ignoreMethodCallExpressionNPESupport");
  /**
   * Checks if the given expression should be ignored for inspection.
   *
   * @param psiExpression expression which can actually violates the nullability
   * @return true if the inspection should be skipped for the {@code psiExpression},
   * otherwise false
   */
  default boolean ignoreMethodCallExpression(@NotNull PsiExpression psiExpression) {
    return false;
  }
}