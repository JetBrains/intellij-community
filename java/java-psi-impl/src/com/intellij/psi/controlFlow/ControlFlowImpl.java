
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.Stack;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class ControlFlowImpl extends AbstractControlFlow {
  private static final Logger LOG = Logger.getInstance(ControlFlowImpl.class);

  private final List<Instruction> myInstructions = new ArrayList<>();
  private final List<PsiElement> myElementsForInstructions = new ArrayList<>();
  private boolean myConstantConditionOccurred;
  private final Map<Instruction, Instruction> myInstructionCache = new HashMap<>();
  private final Stack<PsiElement> myElementStack = new Stack<>();

  protected ControlFlowImpl() {
    super(new Object2LongOpenHashMap<>());
  }

  void addInstruction(Instruction instruction) {
    if (instruction instanceof ReadVariableInstruction || instruction instanceof WriteVariableInstruction) {
      Instruction oldInstruction = myInstructionCache.putIfAbsent(instruction, instruction);
      if (oldInstruction != null) {
        instruction = oldInstruction;
      }
    }
    myInstructions.add(instruction);
    myElementsForInstructions.add(myElementStack.peek());
  }

  public void startElement(PsiElement element) {
    myElementStack.push(element);
    myElementToOffsetMap.put(element, 0xFFFF_FFFF_0000_0000L | myInstructions.size());
    assert getStartOffset(element) == myInstructions.size();
  }

  void finishElement(PsiElement element) {
    PsiElement popped = myElementStack.pop();
    LOG.assertTrue(popped.equals(element));
    myElementToOffsetMap.computeLong(element, (e, old) -> {
      long endOffset = (long)myInstructions.size() << 32;
      return endOffset | (old == null ? 0xFFFF_FFFFL : old & 0xFFFF_FFFFL);
    });
    assert getEndOffset(element) == myInstructions.size();
  }

  @Override
  @NotNull
  public List<Instruction> getInstructions() {
    return myInstructions;
  }
  @Override
  public int getSize() {
    return myInstructions.size();
  }
  
  ControlFlow immutableCopy() {
    return new ImmutableControlFlow(myInstructions.toArray(new Instruction[0]),
                                    new Object2LongOpenHashMap<>(myElementToOffsetMap),
                                    myElementsForInstructions.toArray(PsiElement.EMPTY_ARRAY),
                                    myConstantConditionOccurred);
  }

  @Override
  public PsiElement getElement(int offset) {
    return myElementsForInstructions.get(offset);
  }

  @Override
  public boolean isConstantConditionOccurred() {
    return myConstantConditionOccurred;
  }
  void setConstantConditionOccurred(boolean constantConditionOccurred) {
    myConstantConditionOccurred = constantConditionOccurred;
  }

  private static final class ImmutableControlFlow extends AbstractControlFlow {
    private final @NotNull List<Instruction> myInstructions;
    private final @NotNull PsiElement @NotNull [] myElementsForInstructions;
    private final boolean myConstantConditionOccurred;

    private ImmutableControlFlow(@NotNull Instruction @NotNull [] instructions, 
                                 @NotNull Object2LongMap<PsiElement> myElementToOffsetMap,
                                 @NotNull PsiElement @NotNull [] elementsForInstructions, boolean occurred) {
      super(myElementToOffsetMap);
      myInstructions = Arrays.asList(instructions);
      myElementsForInstructions = elementsForInstructions;
      myConstantConditionOccurred = occurred;
    }

    @Override
    public @NotNull List<Instruction> getInstructions() {
      return myInstructions;
    }

    @Override
    public int getSize() {
      return myInstructions.size();
    }

    @Override
    public PsiElement getElement(int offset) {
      return myElementsForInstructions[offset];
    }

    @Override
    public boolean isConstantConditionOccurred() {
      return myConstantConditionOccurred;
    }
  }
}