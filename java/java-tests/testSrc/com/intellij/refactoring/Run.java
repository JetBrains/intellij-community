package com.intellij.refactoring;

import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 *  @author dsl
 */
public class Run {
  public static void main(String[] args) throws Exception {
    new TestRunner().doRun(new TestSuite(IntroduceVariableTest.class));
  }
}
