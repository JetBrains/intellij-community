package com.intellij.lang.annotation;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 6, 2005
 * Time: 4:23:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class HighlightSeverity {
  private final String myName; // for debug only
  private final int myVal;
  public static final HighlightSeverity INFORMATION = new HighlightSeverity("INFORMATION", 0);
  public static final HighlightSeverity WARNING = new HighlightSeverity("WARNING", 100);
  public static final HighlightSeverity ERROR = new HighlightSeverity("ERROR", 200);

  public HighlightSeverity(String name, int val) {
    myName = name;
    myVal = val;
  }

  public String toString() {
    return myName;
  }

  public boolean isGreaterOrEqual(HighlightSeverity severity) {
    return myVal >= severity.myVal;
  }

  public boolean isLess(HighlightSeverity severity) {
    return myVal < severity.myVal;
  }
}
