// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ControlFlow {
  @NotNull PsiElement getPsiAnchor();

  Instruction[] getInstructions();

  Instruction getInstruction(int index);

  int getInstructionCount();

  ControlFlowOffset getNextOffset();

  void startElement(PsiElement psiElement);

  void finishElement(PsiElement psiElement);

  int[] getLoopNumbers();

  ControlFlowOffset getStartOffset(PsiElement element);

  ControlFlowOffset getEndOffset(PsiElement element);

  @NotNull DfaValueFactory getFactory();

  @NotNull List<DfaVariableValue> getSynthetics(PsiElement element);
}
