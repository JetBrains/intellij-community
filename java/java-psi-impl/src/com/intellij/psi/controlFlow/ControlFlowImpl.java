
// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class ControlFlowImpl implements ControlFlow {
  private static final Logger LOG = Logger.getInstance(ControlFlowImpl.class);

  private final List<Instruction> myInstructions = new ArrayList<>();
  private final ObjectIntHashMap<PsiElement> myElementToStartOffsetMap = new ObjectIntHashMap<>();
  private final ObjectIntHashMap<PsiElement> myElementToEndOffsetMap = new ObjectIntHashMap<>();
  private final List<PsiElement> myElementsForInstructions = new ArrayList<>();
  private boolean myConstantConditionOccurred;

  private final Stack<PsiElement> myElementStack = new Stack<>();

  void addInstruction(Instruction instruction) {
    myInstructions.add(instruction);
    myElementsForInstructions.add(myElementStack.peek());
  }

  public void startElement(PsiElement element) {
    myElementStack.push(element);
    myElementToStartOffsetMap.put(element, myInstructions.size());
  }

  void finishElement(PsiElement element) {
    PsiElement popped = myElementStack.pop();
    LOG.assertTrue(popped.equals(element));
    myElementToEndOffsetMap.put(element, myInstructions.size());
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

  @Override
  public int getStartOffset(@NotNull PsiElement element) {
    return myElementToStartOffsetMap.get(element);
  }

  @Override
  public int getEndOffset(@NotNull PsiElement element) {
    return myElementToEndOffsetMap.get(element);
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

  public String toString() {
    StringBuilder buffer = new StringBuilder();
    for(int i = 0; i < myInstructions.size(); i++){
      Instruction instruction = myInstructions.get(i);
      buffer.append(i).append(": ").append(instruction).append("\n");
    }
    return buffer.toString();
  }
}