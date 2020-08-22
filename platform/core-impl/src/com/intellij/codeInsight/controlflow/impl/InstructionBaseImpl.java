// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.Instruction;
import com.intellij.psi.PsiElement;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class InstructionBaseImpl implements Instruction {

  private final List<Instruction> myPred = new SmartList<>();
  private final List<Instruction> mySucc = new SmartList<>();

  @Nullable
  protected final PsiElement myElement;

  @Override
  @Nullable
  public PsiElement getElement() {
    return myElement;
  }

  public InstructionBaseImpl(@Nullable final PsiElement element) {
    myElement = element;
  }

  @NotNull
  @Override
  public final List<Instruction> allSucc() {
    return mySucc;
  }

  @NotNull
  @Override
  public final List<Instruction> allPred() {
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
