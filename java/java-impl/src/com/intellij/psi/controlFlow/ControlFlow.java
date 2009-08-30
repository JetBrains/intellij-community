/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;

import java.util.List;

public interface ControlFlow {
  ControlFlow EMPTY = new ControlFlowImpl();

  List<Instruction> getInstructions();

  int getSize();

  int getStartOffset(PsiElement element);

  int getEndOffset(PsiElement element);

  PsiElement getElement(int offset);

  // true means there is at least one place where controlflow has been shortcircuited due to constant condition
  // false means no constant conditions were detected affecting control flow
  boolean isConstantConditionOccurred();
}