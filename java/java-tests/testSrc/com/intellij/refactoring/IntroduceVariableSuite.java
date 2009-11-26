package com.intellij.refactoring;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 *  @author dsl
 */
public class IntroduceVariableSuite extends TestCase {
  public static Test suite() {
    final TestSuite suite = new TestSuite();
    suite.addTestSuite(IntroduceVariableTest.class);
    suite.addTestSuite(IntroduceVariableMultifileTest.class);
    return suite;
  }
}
