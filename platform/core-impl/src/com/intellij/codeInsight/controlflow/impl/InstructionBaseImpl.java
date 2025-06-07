// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  protected final @Nullable PsiElement myElement;

  @Override
  public @Nullable PsiElement getElement() {
    return myElement;
  }

  public InstructionBaseImpl(final @Nullable PsiElement element) {
    myElement = element;
  }

  @Override
  public final @NotNull List<Instruction> allSucc() {
    return mySucc;
  }

  @Override
  public final @NotNull List<Instruction> allPred() {
    return myPred;
  }

  @Override
  public @NotNull String toString() {
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

  @Override
  public @NotNull String getElementPresentation() {
    return "element: " + myElement;
  }

  protected @NotNull String id() {
    return String.valueOf(num());
  }
}
