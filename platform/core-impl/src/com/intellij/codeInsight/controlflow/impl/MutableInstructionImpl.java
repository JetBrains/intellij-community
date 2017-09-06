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

import com.intellij.codeInsight.controlflow.ControlFlowBuilder;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

public class MutableInstructionImpl extends InstructionBaseImpl {

  private final AtomicInteger myNum = new AtomicInteger();

  public MutableInstructionImpl(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  public int num() {
    return myNum.get();
  }

  public void insertNode(@NotNull ControlFlowBuilder builder) {
    assert myNum.get() == 0;
    builder.instructions.add(this);
    int newId = builder.instructionCount++;
    myNum.set(newId);
  }

  public void addNode(@NotNull ControlFlowBuilder builder) {
    assert myNum.get() == 0;
    builder.addNode(this);
    int newId = builder.instructionCount++;
    myNum.set(newId);
  }
}
