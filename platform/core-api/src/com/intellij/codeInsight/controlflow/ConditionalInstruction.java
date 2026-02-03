// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow;

import com.intellij.psi.PsiElement;

public interface ConditionalInstruction extends Instruction {
  boolean getResult();
  PsiElement getCondition();
}
