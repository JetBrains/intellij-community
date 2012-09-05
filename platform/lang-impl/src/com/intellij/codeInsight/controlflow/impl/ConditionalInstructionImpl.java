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
import com.intellij.codeInsight.controlflow.ConditionalInstruction;
import com.intellij.psi.PsiElement;

/**
 * @author oleg
 */
public class ConditionalInstructionImpl extends InstructionImpl implements ConditionalInstruction {
  private final PsiElement myCondition;
  private final boolean myResult;

  public ConditionalInstructionImpl(final ControlFlowBuilder builder,
                                    final PsiElement element,
                                    final PsiElement condition,
                                    final boolean result) {
    super(builder, element);
    myCondition = condition;
    myResult = result;
  }

  @Override
  public PsiElement getCondition() {
    return myCondition;
  }

  @Override
  public boolean getResult() {
    return myResult;
  }

  @Override
  public String toString() {
    return super.toString() + ". Condition: " + myCondition.getText() + ":" + myResult;
  }
}
