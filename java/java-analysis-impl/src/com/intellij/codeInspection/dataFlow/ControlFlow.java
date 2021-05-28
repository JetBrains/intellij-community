// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class ControlFlow {
  private final List<Instruction> myInstructions = new ArrayList<>();
  private final Object2IntMap<PsiElement> myElementToStartOffsetMap = new Object2IntOpenHashMap<>();
  private final Object2IntMap<PsiElement> myElementToEndOffsetMap = new Object2IntOpenHashMap<>();
  private final DfaValueFactory myFactory;
  private int[] myLoopNumbers;

  public ControlFlow(final DfaValueFactory factory) {
    myFactory = factory;
  }

  public Instruction[] getInstructions(){
    return myInstructions.toArray(new Instruction[0]);
  }

  public Instruction getInstruction(int index) {
    return myInstructions.get(index);
  }

  public int getInstructionCount() {
    return myInstructions.size();
  }

  public ControlFlowOffset getNextOffset() {
    return new FixedOffset(myInstructions.size());
  }

  public void startElement(PsiElement psiElement) {
    myElementToStartOffsetMap.put(psiElement, myInstructions.size());
  }

  public void finishElement(PsiElement psiElement) {
    myElementToEndOffsetMap.put(psiElement, myInstructions.size());
  }

  public void addInstruction(Instruction instruction) {
    instruction.setIndex(myInstructions.size());
    myInstructions.add(instruction);
  }

  public int[] getLoopNumbers() {
    return myLoopNumbers;
  }

  void finish() {
    addInstruction(new ReturnInstruction(myFactory.controlTransfer(ReturnTransfer.INSTANCE, FList.emptyList()), null));

    myLoopNumbers = LoopAnalyzer.calcInLoop(this);
  }

  public void removeVariable(@Nullable PsiVariable variable) {
    if (variable == null) return;
    addInstruction(new FlushVariableInstruction(myFactory.getVarFactory().createVariableValue(variable)));
  }

  /**
   * @return stream of all accessed variables within this flow
   */
  public Stream<DfaVariableValue> accessedVariables() {
    return StreamEx.of(myInstructions).select(PushInstruction.class)
      .remove(PushInstruction::isReferenceWrite)
      .map(PushInstruction::getValue)
      .select(DfaVariableValue.class).distinct();
  }

  public ControlFlowOffset getStartOffset(final PsiElement element) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return myElementToStartOffsetMap.getInt(element);
      }
    };
  }

  public ControlFlowOffset getEndOffset(final PsiElement element) {
    return new ControlFlowOffset() {
      @Override
      public int getInstructionOffset() {
        return myElementToEndOffsetMap.getInt(element);
      }
    };
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    final List<Instruction> instructions = myInstructions;

    for (int i = 0; i < instructions.size(); i++) {
      Instruction instruction = instructions.get(i);
      result.append(i).append(": ").append(instruction.toString());
      result.append("\n");
    }
    return result.toString();
  }

  void makeNop(int index) {
    SpliceInstruction instruction = new SpliceInstruction(0);
    instruction.setIndex(index);
    myInstructions.set(index, instruction);
  }

  public abstract static class ControlFlowOffset {
    public abstract int getInstructionOffset();

    @Override
    public String toString() {
      return String.valueOf(getInstructionOffset());
    }
  }

  public static class FixedOffset extends ControlFlowOffset {
    private final int myOffset;

    public FixedOffset(int offset) {
      myOffset = offset;
    }

    @Override
    public int getInstructionOffset() {
      return myOffset;
    }
  }

  public static class DeferredOffset extends ControlFlowOffset {
    private int myOffset = -1;

    @Override
    public int getInstructionOffset() {
      if (myOffset == -1) {
        throw new IllegalStateException("Not set");
      }
      return myOffset;
    }

    public void setOffset(int offset) {
      if (myOffset != -1) {
        throw new IllegalStateException("Already set");
      }
      else {
        myOffset = offset;
      }
    }

    @Override
    public String toString() {
      return myOffset == -1 ? "<not set>" : super.toString();
    }
  }
}