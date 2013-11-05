/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.quickFix.ChangeNewOperatorTypeTest;
import com.intellij.codeInsight.daemon.quickFix.Simplify2DiamondInspectionsTest;
import com.intellij.refactoring.IntroduceParameterTest;
import com.intellij.refactoring.IntroduceVariableTest;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * User: anna
 * Date: 3/9/11
 */
public class DiamondSuite {
  private DiamondSuite() {
  }

  public static Test suite() {
    final TestSuite testSuite = new TestSuite("Diamond Suite");
    testSuite.addTestSuite(LightAdvHighlightingJdk7Test.class);
    testSuite.addTestSuite(Simplify2DiamondInspectionsTest.class);
    testSuite.addTestSuite(IntroduceParameterTest.class);
    testSuite.addTestSuite(IntroduceVariableTest.class);
    testSuite.addTestSuite(ChangeNewOperatorTypeTest.class);
    return testSuite;
  }
}
