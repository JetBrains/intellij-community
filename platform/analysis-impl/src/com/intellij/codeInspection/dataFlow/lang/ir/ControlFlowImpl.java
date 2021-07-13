// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents code block IR (list of instructions)
 */
public final class ControlFlowImpl implements ControlFlow {
  private @NotNull final List<Instruction> myInstructions;
  private @NotNull final Object2IntMap<PsiElement> myElementToStartOffsetMap;
  private @NotNull final Object2IntMap<PsiElement> myElementToEndOffsetMap;
  private @NotNull final DfaValueFactory myFactory;
  private @NotNull final PsiElement myPsiAnchor;
  private int[] myLoopNumbers;

  public ControlFlowImpl(@NotNull final DfaValueFactory factory, @NotNull PsiElement psiAnchor) {
    myFactory = factory;
    myPsiAnchor = psiAnchor;
    myInstructions = new ArrayList<>();
    myElementToEndOffsetMap = new Object2IntOpenHashMap<>();
    myElementToStartOffsetMap = new Object2IntOpenHashMap<>();
  }

  /**
   * Copy constructor to bind existing flow to another factory. The newly-created flow should not depend on the original factory
   * but may reuse as much of data as possible. The modifications in new flow are prohibited
   *
   * @param flow    flow to copy
   * @param factory factory to use
   */
  public ControlFlowImpl(@NotNull ControlFlowImpl flow, @NotNull DfaValueFactory factory) {
    myFactory = factory;
    myPsiAnchor = flow.myPsiAnchor;
    myElementToEndOffsetMap = flow.myElementToEndOffsetMap;
    myElementToStartOffsetMap = flow.myElementToStartOffsetMap;
    myLoopNumbers = flow.myLoopNumbers;
    myInstructions = StreamEx.of(flow.myInstructions).map(instruction -> instruction.bindToFactory(factory)).toImmutableList();
  }

  @Override
  public @NotNull PsiElement getPsiAnchor() {
    return myPsiAnchor;
  }

  @Override
  public Instruction[] getInstructions(){
    return myInstructions.toArray(new Instruction[0]);
  }

  @Override
  public Instruction getInstruction(int index) {
    return myInstructions.get(index);
  }

  @Override
  public int getInstructionCount() {
    return myInstructions.size();
  }

  @Override
  public ControlFlowOffset getNextOffset() {
    return new FixedOffset(myInstructions.size());
  }

  @Override
  public void startElement(PsiElement psiElement) {
    myElementToStartOffsetMap.put(psiElement, myInstructions.size());
  }

  @Override
  public void finishElement(PsiElement psiElement) {
    myElementToEndOffsetMap.put(psiElement, myInstructions.size());
  }

  public void addInstruction(Instruction instruction) {
    instruction.setIndex(myInstructions.size());
    myInstructions.add(instruction);
  }

  @Override
  public int[] getLoopNumbers() {
    return myLoopNumbers;
  }

  /**
   * Finalize current control flow. No more instructions are accepted after this call
   */
  public void finish() {
    try {
      addInstruction(new ReturnInstruction(myFactory, FList.emptyList(), null));
      myLoopNumbers = LoopAnalyzer.calcInLoop(this);
      new LiveVariablesAnalyzer(this).flushDeadVariablesOnStatementFinish();
    }
    catch (ProcessCanceledException ex) {
      throw ex;
    }
    catch (RuntimeException ex) {
      throw new RuntimeExceptionWithAttachments(ex, new Attachment("flow.txt", toString()));
    }
  }

  @Override
  public ControlFlowOffset getStartOffset(final PsiElement element) {
    return new FromMapOffset(element, myElementToStartOffsetMap);
  }

  @Override
  public ControlFlowOffset getEndOffset(final PsiElement element) {
    return new FromMapOffset(element, myElementToEndOffsetMap);
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

  /**
   * Replaces instruction at a given index with NOP
   * @param index instruction index to replace
   */
  public void makeNop(int index) {
    SpliceInstruction instruction = new SpliceInstruction(0);
    instruction.setIndex(index);
    myInstructions.set(index, instruction);
  }

  @Override
  public @NotNull DfaValueFactory getFactory() {
    return myFactory;
  }

  /**
   * Create a synthetic variable (not declared in the original code) to be used within this control flow.
   *
   * @param dfType a type of variable to create
   * @return newly created variable
   */
  public @NotNull DfaVariableValue createTempVariable(@NotNull DfType dfType) {
    return getFactory().getVarFactory().createVariableValue(new Synthetic(getInstructionCount(), dfType));
  }

  @Override
  public @NotNull List<DfaVariableValue> getSynthetics(PsiElement element) {
    int startOffset = getStartOffset(element).getInstructionOffset();
    List<DfaVariableValue> synthetics = new ArrayList<>();
    for (DfaValue value : myFactory.getValues()) {
      if (value instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)value;
        VariableDescriptor descriptor = var.getDescriptor();
        if (descriptor instanceof Synthetic && ((Synthetic)descriptor).getLocation() >= startOffset) {
          synthetics.add(var);
        }
      }
    }
    return synthetics;
  }

  private static class FromMapOffset extends ControlFlowOffset {
    private final PsiElement myElement;
    private final Object2IntMap<PsiElement> myElementMap;

    private FromMapOffset(PsiElement element, Object2IntMap<PsiElement> map) {
      myElement = element;
      myElementMap = map;
    }

    @Override
    public int getInstructionOffset() {
      return myElementMap.getInt(myElement);
    }
  }
}