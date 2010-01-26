package com.intellij.codeInsight.codeFragment;

import java.util.Set;

/**
 * @author oleg
 */
public class CodeFragment {
  private final Set<String> inputVariables;
  private final Set<String> outputVariables;
  private final boolean returnInstructonInside;

  public CodeFragment(final Set<String> input, final Set<String> output, final boolean returnInside) {
    inputVariables = input;
    outputVariables = output;
    returnInstructonInside = returnInside;
  }

  public Set<String> getInputVariables() {
    return inputVariables;
  }

  public Set<String> getOutputVariables() {
    return outputVariables;
  }

  public boolean isReturnInstructonInside() {
    return returnInstructonInside;
  }
}
