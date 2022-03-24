// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Determines if passed element is inside code fragment where thread should not be blocked.
 * <p>
 * Frameworks could implement this EP to provide information that in some contexts the execution thread should not be blocked.
 */
public interface NonBlockingContextChecker {
  ExtensionPointName<NonBlockingContextChecker> EP_NAME = ExtensionPointName.create("com.intellij.codeInsight.nonBlockingContextChecker");

  /**
   * @return true if current extension can detect non-blocking contexts in the given {@code file}
   */
  boolean isApplicable(@NotNull PsiFile file);

  /**
   * @param elementContext metadata that provides info about inspection settings and PsiElement (e.g. method call or reference)
   *                      to check if it is placed in code fragment where thread block is not allowed
   * @return true if code fragment for {@code element} is considered "non-blocking", false otherwise
   */
  default ContextType computeContextType(@NotNull ElementContext elementContext) {
    if (isContextNonBlockingFor(elementContext.getElement())) {
      return ContextType.NonBlocking.INSTANCE;
    }
    else {
      return ContextType.Blocking.INSTANCE;
    }
  }

  /**
   * @deprecated Override {@link #computeContextType(ElementContext)} instead.
   */
  @Deprecated(forRemoval = true)
  default boolean isContextNonBlockingFor(@NotNull PsiElement element) {
    return false;
  }
}
