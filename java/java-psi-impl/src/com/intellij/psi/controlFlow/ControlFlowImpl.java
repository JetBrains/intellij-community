
/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.util.containers.Stack;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

class ControlFlowImpl implements ControlFlow {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ControlFlowImpl");

  private final List<Instruction> myInstructions = new ArrayList<Instruction>();
  private final TObjectIntHashMap<PsiElement> myElementToStartOffsetMap = new TObjectIntHashMap<PsiElement>();
  private final TObjectIntHashMap<PsiElement> myElementToEndOffsetMap = new TObjectIntHashMap<PsiElement>();
  private final List<PsiElement> myElementsForInstructions = new ArrayList<PsiElement>();
  private boolean myConstantConditionOccurred;

  private final Stack<PsiElement> myElementStack = new Stack<PsiElement>();

  public void addInstruction(Instruction instruction) {
    myInstructions.add(instruction);
    myElementsForInstructions.add(myElementStack.peek());
  }

  public void startElement(PsiElement element) {
    myElementStack.push(element);
    myElementToStartOffsetMap.put(element, myInstructions.size());

    if (LOG.isDebugEnabled()){
      if (element instanceof PsiStatement){
        String text = element.getText();
        int index = Math.min(text.indexOf('\n'), text.indexOf('\r'));
        if (index >= 0){
          text = text.substring(0, index);
        }
        addInstruction(new CommentInstruction(text));
      }
    }
  }

  public void finishElement(PsiElement element) {
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
    int value = myElementToStartOffsetMap.get(element);
    if (value == 0){
      if (!myElementToStartOffsetMap.containsKey(element)) return -1;
    }
    return value;
  }

  @Override
  public int getEndOffset(@NotNull PsiElement element) {
    int value = myElementToEndOffsetMap.get(element);
    if (value == 0){
      if (!myElementToEndOffsetMap.containsKey(element)) return -1;
    }
    return value;
  }

  @Override
  public PsiElement getElement(int offset) {
    return myElementsForInstructions.get(offset);
  }

  @Override
  public boolean isConstantConditionOccurred() {
    return myConstantConditionOccurred;
  }
  public void setConstantConditionOccurred(boolean constantConditionOccurred) {
    myConstantConditionOccurred = constantConditionOccurred;
  }

  public String toString() {
    StringBuilder buffer = new StringBuilder();
    for(int i = 0; i < myInstructions.size(); i++){
      Instruction instruction = myInstructions.get(i);
      buffer.append(Integer.toString(i));
      buffer.append(": ");
      buffer.append(instruction.toString());
      buffer.append("\n");
    }
    return buffer.toString();
  }
}