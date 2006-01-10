/*
 * Copyright (c) 2006 Your Corporation. All Rights Reserved.
 */
package com.intellij.codeInspection;

/**
 * User: anna
 * Date: 04-Jan-2006
 */
public class CommonProblemDescriptorImpl implements CommonProblemDescriptor {
  private final QuickFix[] myFixes;
  private final String myDescriptionTemplate;

  public CommonProblemDescriptorImpl(final QuickFix[] fixes, final String descriptionTemplate) {
    if (fixes != null) {
      myFixes = new QuickFix[fixes.length];
      System.arraycopy(fixes, 0, myFixes, 0, fixes.length);
    } else {
      myFixes = null;
    }
    myDescriptionTemplate = descriptionTemplate;
  }

  public String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  public QuickFix[] getFixes() {
    return myFixes;
  }
}
