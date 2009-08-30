
package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import gnu.trove.TObjectIntHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

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
    LOG.assertTrue(myElementStack.pop().equals(element));
    myElementToEndOffsetMap.put(element, myInstructions.size());
  }

  public List<Instruction> getInstructions() {
    return myInstructions;
  }
  public int getSize() {
    return myInstructions.size();
  }

  public int getStartOffset(PsiElement element) {
    int value = myElementToStartOffsetMap.get(element);
    if (value == 0){
      if (!myElementToStartOffsetMap.containsKey(element)) return -1;
    }
    return value;
  }

  public int getEndOffset(PsiElement element) {
    int value = myElementToEndOffsetMap.get(element);
    if (value == 0){
      if (!myElementToEndOffsetMap.containsKey(element)) return -1;
    }
    return value;
  }

  public PsiElement getElement(int offset) {
    return myElementsForInstructions.get(offset);
  }

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