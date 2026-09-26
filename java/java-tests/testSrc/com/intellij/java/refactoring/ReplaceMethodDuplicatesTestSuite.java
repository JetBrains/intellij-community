// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import junit.framework.Test;
import junit.framework.TestSuite;

public final class ReplaceMethodDuplicatesTestSuite {
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