// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

abstract class AbstractControlFlow implements ControlFlow {
  // Low 4 bytes = start; high 4 bytes = end 
  final @NotNull Object2LongMap<PsiElement> myElementToOffsetMap;

  AbstractControlFlow(@NotNull Object2LongMap<PsiElement> map) {
    myElementToOffsetMap = map;
  }

  @Override
  public int getStartOffset(@NotNull PsiElement element) {
    return (int)(myElementToOffsetMap.getOrDefault(element, -1L) & 0xFFFF_FFFFL);
  }

  @Override
  public int getEndOffset(@NotNull PsiElement element) {
    return (int)(myElementToOffsetMap.getOrDefault(element, -1L) >>> 32);
  }

  public String toString() {
    StringBuilder buffer = new StringBuilder();
    List<Instruction> instructions = getInstructions();
    for(int i = 0; i < instructions.size(); i++){
      Instruction instruction = instructions.get(i);
      buffer.append(i).append(": ").append(instruction).append("\n");
    }
    return buffer.toString();
  }
}
