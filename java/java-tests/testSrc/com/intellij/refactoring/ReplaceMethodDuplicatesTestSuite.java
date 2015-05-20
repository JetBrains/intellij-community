/*
 * User: anna
 * Date: 11-Jun-2009
 */
package com.intellij.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

public class ReplaceMethodDuplicatesTestSuite {
  private ReplaceMethodDuplicatesTestSuite() {
  }

  public static Test suite() {
    final TestSuite testSuite = new TestSuite("Replace Duplicates Suite");
    testSuite.addTestSuite(ExtractMethod15Test.class);
    testSuite.addTestSuite(ExtractMethodTest.class);
    testSuite.addTestSuite(ExtractMethodObjectWithMultipleExitPointsTest.class);
    testSuite.addTestSuite(ExtractMethodObjectTest.class);
    testSuite.addTestSuite(FindMethodDuplicatesMiscTest.class);
    testSuite.addTestSuite(FindMethodDuplicatesTest.class);
    testSuite.addTestSuite(SuggestedParamTypesTest.class);
    testSuite.addTestSuite(SuggestedReturnTypesTest.class);
    return testSuite;
  }
}