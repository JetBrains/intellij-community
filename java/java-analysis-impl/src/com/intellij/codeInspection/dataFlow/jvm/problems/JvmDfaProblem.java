// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class JvmDfaProblem<T extends PsiElement> implements UnsatisfiedConditionProblem {
  private final @NotNull T myAnchor;

  protected JvmDfaProblem(@NotNull T anchor) { 
    myAnchor = anchor; 
  }

  @NotNull
  public T getAnchor() {
    return myAnchor;
  }
}
