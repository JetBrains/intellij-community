// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeFragment;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class CodeFragment {
  private final List<String> inputVariables;
  private final List<String> outputVariables;
  private final boolean returnInstructionInside;

  public CodeFragment(final Set<String> input, final Set<String> output, final boolean returnInside) {
    inputVariables = ContainerUtil.sorted(input);
    outputVariables = ContainerUtil.sorted(output);
    returnInstructionInside = returnInside;
  }

  public @Unmodifiable Collection<String> getInputVariables() {
    return inputVariables;
  }

  public @Unmodifiable Collection<String> getOutputVariables() {
    return outputVariables;
  }

  public boolean isReturnInstructionInside() {
    return returnInstructionInside;
  }
}
