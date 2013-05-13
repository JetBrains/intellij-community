package com.intellij.codeInspection;

import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 04-Jan-2006
 */
public class CommonProblemDescriptorImpl implements CommonProblemDescriptor {
  private final QuickFix[] myFixes;
  private final String myDescriptionTemplate;

  public CommonProblemDescriptorImpl(final QuickFix[] fixes, @NotNull final String descriptionTemplate) {
    if (fixes == null) {
      myFixes = null;
    }
    else if (fixes.length == 0) {
      myFixes = QuickFix.EMPTY_ARRAY;
    }
    else {
      myFixes = fixes;
    }
    myDescriptionTemplate = descriptionTemplate;
  }

  @Override
  @NotNull
  public String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  @Override
  public QuickFix[] getFixes() {
    return myFixes;
  }

  public String toString() {
    return myDescriptionTemplate;
  }
}
