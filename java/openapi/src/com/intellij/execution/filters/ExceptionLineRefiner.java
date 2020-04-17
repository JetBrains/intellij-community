// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.PsiElement;

import java.util.function.Predicate;

/**
 * Allows to refine the stacktrace navigation position based on the previous line 
 */
@FunctionalInterface
interface ExceptionLineRefiner extends Predicate<PsiElement> {
  default ExceptionInfo getExceptionInfo() {
    return null;
  }
}
