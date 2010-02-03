package com.intellij.codeInsight.codeFragment;

import java.util.*;

/**
 * @author oleg
 */
public class CodeFragment {
  private final List<String> inputVariables;
  private final List<String> outputVariables;
  private final boolean returnInstructonInside;

  public CodeFragment(final Set<String> input, final Set<String> output, final boolean returnInside) {
    inputVariables = new ArrayList<String>(input);
    Collections.sort(inputVariables);
    outputVariables = new ArrayList<String>(output);
    Collections.sort(outputVariables);
    returnInstructonInside = returnInside;
  }

  public Collection<String> getInputVariables() {
    return inputVariables;
  }

  public Collection<String> getOutputVariables() {
    return outputVariables;
  }

  public boolean isReturnInstructonInside() {
    return returnInstructonInside;
  }
}
