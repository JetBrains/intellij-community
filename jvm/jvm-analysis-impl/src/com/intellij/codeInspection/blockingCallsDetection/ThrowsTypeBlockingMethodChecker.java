// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

final class ThrowsTypeBlockingMethodChecker implements BlockingMethodChecker {
  private static final Set<String> BLOCKING_EXCEPTION_TYPES = Set.of("java.lang.InterruptedException", "java.io.IOException");
  private static final Set<String> NON_BLOCKING_EXCEPTION_TYPES = Set.of("java.net.MalformedURLException");

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    return true;
  }

  @Override
  public boolean isMethodBlocking(@NotNull MethodContext context) {
    if (!isInStandardLibrary(context.getElement())) return false;

    for (PsiClassType throwType : context.getElement().getThrowsList().getReferencedTypes()) {
      PsiClass resolvedExceptionClass = throwType.resolve();
      if (resolvedExceptionClass == null) continue;

      for (String exceptionFqn : BLOCKING_EXCEPTION_TYPES) {
        if (isBlockingException(resolvedExceptionClass, exceptionFqn)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isBlockingException(PsiClass resolvedExceptionClass, String blockingFqn) {
    return InheritanceUtil.isInheritor(resolvedExceptionClass, blockingFqn)
           && !NON_BLOCKING_EXCEPTION_TYPES.contains(resolvedExceptionClass.getQualifiedName());
  }

  private static boolean isInStandardLibrary(PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return false;
    String classQualifiedName = containingClass.getQualifiedName();
    return classQualifiedName != null
           && (classQualifiedName.startsWith("java.")
               || classQualifiedName.startsWith("javax.")
               || classQualifiedName.startsWith("jakarta."));
  }
}
