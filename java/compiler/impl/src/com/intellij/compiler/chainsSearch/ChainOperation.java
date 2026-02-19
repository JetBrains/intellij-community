// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.chainsSearch;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public interface ChainOperation {
  class TypeCast implements ChainOperation {
    // we cast only to a class
    private final PsiClass myOperandClass;
    private final @NotNull PsiClass myCastClass;

    public TypeCast(@NotNull PsiClass operandClass, @NotNull PsiClass castClass) {
      myOperandClass= operandClass;
      myCastClass = castClass;
    }

    public @NotNull PsiClass getCastClass() {
      return myCastClass;
    }

    @Override
    public String toString() {
      return "cast of " + myOperandClass.getName();
    }
  }

  class MethodCall implements ChainOperation {
    private final PsiMethod @NotNull [] myCandidates;

    public MethodCall(PsiMethod @NotNull [] candidates) {
      if (candidates.length == 0) {
        throw new IllegalStateException();
      }
      myCandidates = candidates;
    }

    public PsiMethod @NotNull [] getCandidates() {
      return myCandidates;
    }

    @Override
    public String toString() {
      return myCandidates[0].getName() + "()";
    }
  }
}
