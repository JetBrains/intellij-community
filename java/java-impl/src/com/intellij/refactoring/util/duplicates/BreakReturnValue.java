/*
 * User: anna
 * Date: 23-Oct-2008
 */
package com.intellij.refactoring.util.duplicates;

import org.jetbrains.annotations.NonNls;

public class BreakReturnValue extends GotoReturnValue{
  public boolean isEquivalent(final ReturnValue other) {
    return other instanceof BreakReturnValue;
  }

  @NonNls
  public String getGotoStatement() {
    return "if(a) break;";
  }
}