// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.containers.FList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class ControlFlow {
  private @NotNull final List<Instruction> myInstructions;
  private @NotNull final Object2IntMap<PsiElement> myElementToStartOffsetMap;
  private @NotNull final Object2IntMap<PsiElement> myElementToEndOffsetMap;
  private @NotNull final DfaValueFactory myFactory;
  private int[] myLoopNumbers;

  ControlFlow(@NotNull final DfaValueFactory factory) {
    myFactory = factory;
    myInstructions = new ArrayList<>();
    myElementToEndOffsetMap = new Object2IntOpenHashMap<>();
    myElementToStartOffsetMap = new Object2IntOpenHashMap<>();
  }

  /**
   * Copy constructor to bind existing flow to another factory. The newly-created flow should not depend on the original factory
   * but may reuse as much of data as possible. The modifications in new flow are prohibited
   *
   * @param flow flow to copy
   * @param factory factory to use
   */
  private ControlFlow(@NotNull ControlFlow flow, @NotNull DfaValueFactory factory) {
    myFactory = factory;
    myElementToEndOffsetMap = flow.myElementToEndOffsetMap;
    myElementToStartOffsetMap = flow.myElementToStartOffsetMap;
    myLoopNumbers = flow.myLoopNumbers;
    myInstructions = StreamEx.of(flow.myInstructions).map(instruction -> instruction.bindToFactory(factory)).toImmutableList();
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
    new LiveVariablesAnalyzer(this).flushDeadVariablesOnStatementFinish();
  }

  public void removeVariable(@Nullable PsiVariable variable) {
    if (variable == null) return;
    addInstruction(new FlushVariableInstruction(PlainDescriptor.createVariableValue(myFactory, variable)));
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
    return new FromMapOffset(element, myElementToStartOffsetMap);
  }

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

  void makeNop(int index) {
    SpliceInstruction instruction = new SpliceInstruction(0);
    instruction.setIndex(index);
    myInstructions.set(index, instruction);
  }

  public @NotNull DfaValueFactory getFactory() {
    return myFactory;
  }

  /**
   * Create control flow for given PSI block (method body, lambda expression, etc.) and return it. May return cached block.
   * It's prohibited to change the resulting control flow (e.g. add instructions, update their indices, update flush variable lists, etc.)
   *
   * @param psiBlock psi-block
   * @param targetFactory factory to bind the PSI block to
   * @param useInliners whether to use inliners
   * @return resulting control flow; null if cannot be built (e.g. if the code block contains unrecoverable errors)
   */
  @Nullable
  public static ControlFlow buildFlow(@NotNull PsiElement psiBlock, DfaValueFactory targetFactory, boolean useInliners) {
    if (!useInliners) {
      return new ControlFlowAnalyzer(targetFactory, psiBlock, false).buildControlFlow();
    }
    PsiFile file = psiBlock.getContainingFile();
    ConcurrentHashMap<PsiElement, Optional<ControlFlow>> fileMap =
      CachedValuesManager.getCachedValue(file, () ->
        CachedValueProvider.Result.create(new ConcurrentHashMap<>(), PsiModificationTracker.MODIFICATION_COUNT));
    return fileMap.computeIfAbsent(psiBlock, psi -> {
      DfaValueFactory factory = new DfaValueFactory(file.getProject(), psiBlock);
      ControlFlow flow = new ControlFlowAnalyzer(factory, psiBlock, true).buildControlFlow();
      return Optional.ofNullable(flow);
    }).map(flow -> new ControlFlow(flow, targetFactory)).orElse(null);
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