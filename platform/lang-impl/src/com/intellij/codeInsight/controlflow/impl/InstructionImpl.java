/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author oleg
 */
public class InstructionImpl implements Instruction {
  final List<Instruction> myPred = new SmartList<Instruction> ();
  final List<Instruction> mySucc = new SmartList<Instruction>();

  protected final PsiElement myElement;
  private final int myNumber;

  @Override
  @Nullable
  public final PsiElement getElement() {
    return myElement;
  }

  public InstructionImpl(final ControlFlowBuilder builder, final PsiElement element) {
    myElement = element;
    myNumber = builder.instructionCount++;
  }

  @Override
  public final Collection<Instruction> allSucc() {
    return mySucc;
  }

  @Override
  public final Collection<Instruction> allPred() {
    return myPred;
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append(myNumber);
    builder.append("(");
    for (int i = 0; i < mySucc.size(); i++) {
      if (i > 0) builder.append(',');
      builder.append(mySucc.get(i).num());
    }
    builder.append(") ").append(getElementPresentation());
    return builder.toString();
  }

  @Override
  public String getElementPresentation() {
    final StringBuffer buffer = new StringBuffer();
    buffer.append("element: ").append(myElement);
    return buffer.toString();
  }

  @Override
  public final int num() {
    return myNumber;
  }
}