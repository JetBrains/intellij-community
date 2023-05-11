// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeFragment;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public class CodeFragment {
  private final List<String> inputVariables;
  private final List<String> outputVariables;
  private final boolean returnInstructionInside;

  public CodeFragment(final Set<String> input, final Set<String> output, final boolean returnInside) {
    inputVariables = ContainerUtil.sorted(input);
    outputVariables = ContainerUtil.sorted(output);
    returnInstructionInside = returnInside;
  }

  @Unmodifiable
  public Collection<String> getInputVariables() {
    return inputVariables;
  }

  @Unmodifiable
  public Collection<String> getOutputVariables() {
    return outputVariables;
  }

  public boolean isReturnInstructionInside() {
    return returnInstructionInside;
  }
}
