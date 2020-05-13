// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.codeFragment;

import java.util.*;

public class CodeFragment {
  private final List<String> inputVariables;
  private final List<String> outputVariables;
  private final boolean returnInstructionInside;

  public CodeFragment(final Set<String> input, final Set<String> output, final boolean returnInside) {
    inputVariables = new ArrayList<>(input);
    Collections.sort(inputVariables);
    outputVariables = new ArrayList<>(output);
    Collections.sort(outputVariables);
    returnInstructionInside = returnInside;
  }

  public Collection<String> getInputVariables() {
    return inputVariables;
  }

  public Collection<String> getOutputVariables() {
    return outputVariables;
  }

  public boolean isReturnInstructionInside() {
    return returnInstructionInside;
  }
}
