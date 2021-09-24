// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.blockingCallsDetection;

import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class MethodContext {

  private final PsiMethod myMethod;
  private final BlockingMethodChecker myCurrentChecker;
  private final Collection<BlockingMethodChecker> myCheckers;

  public MethodContext(@NotNull PsiMethod method,
                       @NotNull BlockingMethodChecker currentChecker,
                       @NotNull Collection<BlockingMethodChecker> checkers) {
    myMethod = method;
    myCurrentChecker = currentChecker;
    myCheckers = checkers;
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  public @NotNull Collection<BlockingMethodChecker> getCheckers() {
    return myCheckers;
  }

  public boolean isMethodNonBlocking() {
    for (BlockingMethodChecker checker : myCheckers) {
      if (myCurrentChecker != checker) {
        if (checker.isMethodNonBlocking(new MethodContext(myMethod, checker, myCheckers))) {
          return true;
        }
      }
    }
    return false;
  }
}
