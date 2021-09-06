// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class MethodContext {

  private final PsiMethod myMethod;
  private final List<BlockingMethodChecker> myCheckers;

  public MethodContext(@NotNull PsiMethod method, @NotNull List<BlockingMethodChecker> checkers) {
    myMethod = method;
    myCheckers = checkers;
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  public @NotNull List<BlockingMethodChecker> getCheckers() {
    return myCheckers;
  }

  public boolean isMethodNonBlocking() {
    for (BlockingMethodChecker checker : myCheckers) {
      if (checker.isMethodNonBlocking(this)) return true;
    }
    return false;
  }
}
