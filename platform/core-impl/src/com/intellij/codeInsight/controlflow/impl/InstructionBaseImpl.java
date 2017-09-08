/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public abstract class InstructionBaseImpl implements Instruction {

  private final List<Instruction> myPred = new SmartList<>();
  private final List<Instruction> mySucc = new SmartList<>();

  @Nullable
  protected final PsiElement myElement;

  @Override
  @Nullable
  public final PsiElement getElement() {
    return myElement;
  }

  public InstructionBaseImpl(@Nullable final PsiElement element) {
    myElement = element;
  }

  @NotNull
  @Override
  public final Collection<Instruction> allSucc() {
    return mySucc;
  }

  @NotNull
  @Override
  public final Collection<Instruction> allPred() {
    return myPred;
  }

  @NotNull
  public String toString() {
    final StringBuilder builder = new StringBuilder(id());
    builder.append("(");
    for (int i = 0; i < mySucc.size(); i++) {
      if (i > 0) builder.append(',');
      Instruction instruction = mySucc.get(i);
      int num = instruction.num();
      if (instruction instanceof InstructionBaseImpl) {
        builder.append(((InstructionBaseImpl)instruction).id());
      }
      else {
        builder.append(num);
      }
    }
    builder.append(") ").append(getElementPresentation());
    return builder.toString();
  }

  @NotNull
  @Override
  public String getElementPresentation() {
    return "element: " + myElement;
  }

  @NotNull
  protected String id() {
    return String.valueOf(num());
  }
}
