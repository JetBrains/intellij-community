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
import com.intellij.codeInsight.controlflow.TransparentInstruction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TransparentInstructionImpl extends DetachedInstructionImpl implements TransparentInstruction {
  
  @NotNull
  private final String myMarkerName;

  public TransparentInstructionImpl(@Nullable PsiElement element, @NotNull String markerName) {
    super(element);
    myMarkerName = markerName;
  }

  public TransparentInstructionImpl(@Nullable PsiElement element, @NotNull String markerName, @NotNull ControlFlowBuilder builder) {
    super(element, builder);
    myMarkerName = markerName;
  }

  @NotNull
  @Override
  public String getElementPresentation() {
    return super.getElementPresentation() + " marker(" + myMarkerName + ")";
  }
}
