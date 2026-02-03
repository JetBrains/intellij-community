// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface Instruction {

  Instruction[] EMPTY_ARRAY = new Instruction[0];

  /**
   * @return related psi elements. Can be null for fake instructions e.g. entry and exit points
   */
  @Nullable
  PsiElement getElement();

  /**
   * Outgoing edges
   */
  @NotNull
  Collection<Instruction> allSucc();

  /**
   * Incoming edges
   */
  @NotNull
  Collection<Instruction> allPred();

  int num();

  /**
   * element presentation is used in toString() for dumping the graph
   */
  @NotNull
  @NonNls String getElementPresentation();

  default void addSucc(@NotNull Instruction endInstruction) {
    if (!allSucc().contains(endInstruction)) {
      allSucc().add(endInstruction);
    }
  }

  default void addPred(@NotNull Instruction beginInstruction) {
    if (!allPred().contains(beginInstruction)) {
      allPred().add(beginInstruction);
    }
  }

  default void replacePred(@NotNull Instruction oldInstruction, @NotNull Collection<? extends Instruction> newInstructions) {
    newInstructions.forEach(el -> addPred(el));
    allPred().remove(oldInstruction);
  }

  default void replaceSucc(@NotNull Instruction oldInstruction, @NotNull Collection<? extends Instruction> newInstructions) {
    newInstructions.forEach(el -> addSucc(el));
    allSucc().remove(oldInstruction);
  }
}
