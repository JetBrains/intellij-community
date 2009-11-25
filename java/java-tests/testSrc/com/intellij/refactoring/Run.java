package com.intellij.refactoring;

import junit.textui.TestRunner;
import junit.framework.TestSuite;
import com.intellij.TestAll;

/**
 *  @author dsl
 */
public class Run {
  public static void main(String[] args) throws Exception {
    new TestRunner().doRun(new TestSuite(IntroduceVariableTest.class));
  }
}
