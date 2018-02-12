/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.refactoring;

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