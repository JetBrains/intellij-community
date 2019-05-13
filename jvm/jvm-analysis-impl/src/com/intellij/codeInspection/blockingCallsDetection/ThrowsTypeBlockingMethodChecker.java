// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

public class ThrowsTypeBlockingMethodChecker implements BlockingMethodChecker {
  private static final Set<String> BLOCKING_EXCEPTION_TYPES = ContainerUtil.immutableSet(
    "java.lang.InterruptedException",
    "java.io.IOException");

  @Override
  public boolean isApplicable(@NotNull PsiFile file) {
    return true;
  }

  @Override
  public boolean isMethodBlocking(@NotNull PsiMethod method) {
    return Arrays.stream(method.getThrowsList().getReferenceElements())
      .anyMatch(thrownException -> BLOCKING_EXCEPTION_TYPES.contains(thrownException.getQualifiedName()));
  }
}
