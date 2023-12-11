// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DataFlowIRProvider;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaDataFlowIRProvider implements DataFlowIRProvider {
  @Override
  public @Nullable ControlFlow createControlFlow(@NotNull DfaValueFactory factory, @NotNull PsiElement psiBlock) {
    return new ControlFlowAnalyzer(factory, psiBlock, true).buildControlFlow();
  }
}
