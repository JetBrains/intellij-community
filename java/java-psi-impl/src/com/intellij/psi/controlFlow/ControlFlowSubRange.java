// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ControlFlowSubRange implements ControlFlow {
  private final ControlFlow myControlFlow;
  private final int myStart;
  private final int myEnd;
  private List<Instruction> myInstructions;

  public ControlFlowSubRange(ControlFlow controlFlow, int start, int end) {
    myControlFlow = controlFlow;
    myStart = start;
    myEnd = end;
  }

  @Override
  public @NotNull List<Instruction> getInstructions() {
    if (myInstructions == null) {
      final List<Instruction> list = new ArrayList<>(myEnd - myStart);
      final List<Instruction> oldList = myControlFlow.getInstructions();
      for (int i = myStart; i < myEnd; i++) {
        Instruction instruction = oldList.get(i).clone();
        if (instruction instanceof BranchingInstruction) {
          BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
          branchingInstruction.offset = patchOffset(branchingInstruction.offset);
        }
        if (instruction instanceof CallInstruction) {
          final CallInstruction callInstruction = (CallInstruction)instruction;
          callInstruction.procBegin = patchOffset(callInstruction.procBegin);
          callInstruction.procEnd = patchOffset(callInstruction.procEnd);
        }
        if (instruction instanceof ReturnInstruction) {
          final ReturnInstruction returnInstruction = (ReturnInstruction)instruction;
          CallInstruction callInstruction = new CallInstruction(patchOffset(returnInstruction.getProcBegin()), patchOffset(returnInstruction.getProcEnd()));
          returnInstruction.setCallInstruction(callInstruction);
        }
        list.add(instruction);
      }
      myInstructions = list;
    }
    return myInstructions;
  }

  private int patchOffset(int offset) {
    if (offset < myStart) offset = myStart;
    else if (offset > myEnd) offset = myEnd;
    offset -= myStart;
    return offset;
  }

  @Override
  public int getSize() {
    return myEnd - myStart;
  }

  @Override
  public int getStartOffset(@NotNull PsiElement element) {
    return patchOffset(myControlFlow.getStartOffset(element));
    //return (myControlFlow.getStartOffset(element));
  }

  @Override
  public int getEndOffset(@NotNull PsiElement element) {
    return patchOffset(myControlFlow.getEndOffset(element));
    //return myControlFlow.getEndOffset(element);
  }

  @Override
  public PsiElement getElement(int offset) {
    return myControlFlow.getElement(myStart + offset);
  }

  @Override
  public boolean isConstantConditionOccurred() {
    return myControlFlow.isConstantConditionOccurred();
  }

  @Override
  public String toString() {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.
      append("CF range:[").
      append(myStart).
      append("-").
      append(myEnd).
      append("]\n");

    final List<Instruction> instructions = getInstructions();
    for(int i = 0; i < instructions.size(); i++){
      Instruction instruction = instructions.get(i);
      buffer.append(i).append(": ").append(instruction.toString()).append("\n");
    }
    return buffer.toString();
  }
}

