/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ControlFlow {
  ControlFlow EMPTY = new ControlFlowImpl();

  @NotNull
  List<Instruction> getInstructions();

  int getSize();

  int getStartOffset(@NotNull PsiElement element);

  int getEndOffset(@NotNull PsiElement element);

  PsiElement getElement(int offset);

  // true means there is at least one place where controlflow has been shortcircuited due to constant condition
  // false means no constant conditions were detected affecting control flow
  boolean isConstantConditionOccurred();
}