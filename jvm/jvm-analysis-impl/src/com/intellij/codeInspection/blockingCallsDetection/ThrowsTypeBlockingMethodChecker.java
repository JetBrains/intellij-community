// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;

public class ThrowsTypeBlockingMethodChecker implements BlockingMethodChecker {
  private static final HashSet<String> BLOCKING_EXCEPTION_TYPES = new HashSet<>(Arrays.asList(
    "java.lang.InterruptedException",
    "java.io.IOException"));

  @Override
  public boolean isActive(Project project) {
    return !BLOCKING_EXCEPTION_TYPES.isEmpty();
  }

  @Override
  public boolean isMethodBlocking(@NotNull PsiMethod method) {
    return Arrays.stream(method.getThrowsList().getReferenceElements())
      .anyMatch(thrownException -> BLOCKING_EXCEPTION_TYPES.contains(thrownException.getQualifiedName()));
  }
}
