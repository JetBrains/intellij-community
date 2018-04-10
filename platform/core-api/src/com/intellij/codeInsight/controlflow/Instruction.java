/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.controlflow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author oleg
 */
public interface Instruction {
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
  String getElementPresentation();
  
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
  
  default void replacePred(@NotNull Instruction oldInstruction, @NotNull Collection<Instruction> newInstructions) {
    newInstructions.forEach(el -> addPred(el));
    allPred().remove(oldInstruction);
  }

  default void replaceSucc(@NotNull Instruction oldInstruction, @NotNull Collection<Instruction> newInstructions) {
    newInstructions.forEach(el -> addSucc(el));
    allSucc().remove(oldInstruction);
  }
}
