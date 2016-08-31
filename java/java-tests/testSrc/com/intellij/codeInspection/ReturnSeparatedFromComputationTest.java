/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.intermediaryVariable.ReturnSeparatedFromComputationInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Pavel.Dolgov
 */
public class ReturnSeparatedFromComputationTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/returnSeparatedFromComputation";
  }

  public void testReturnOutsideTryWithResources() {
    doTest();
  }

  public void testBreakFromLoopInTryWithResources() {
    doTest();
  }

  public void testSimpleIf() {
    doTest();
  }

  public void testSimpleFor() {
    doTest();
  }

  public void testIfElseWriteInBoth() {
    doTest();
  }

  public void testIfElseWriteInIf() {
    doTest();
  }

  public void testIfElseWriteInElse() {
    doTest();
  }

  public void testIfElseNoWrite() {
    doTest();
  }

  public void testNestedIf() {
    doTest();
  }

  public void testNestedIfInnerElse() {
    doTest();
  }

  public void testNestedIfOuterElse() {
    doTest();
  }

  public void testNestedBlock() {
    doTest();
  }

  public void testNestedBlockSideEffect() {
    doTest();
  }

  public void testAssert() {
    doTest();
  }

  public void testLabeledBlock() {
    doTest();
  }

  public void testLabeledFor() {
    doTest();
  }

  public void testLabeledFor2() {
    doTest();
  }

  public void testLabeledIf() {
    doTest();
  }

  public void testWhileTrue() {
    doTest();
  }

  public void testSimpleWhile() {
    doTest();
  }

  public void testForWithoutCondition() {
    doTest();
  }

  public void testSimpleForeach() {
    doTest();
  }

  public void testDoWhileTrue() {
    doTest();
  }

  public void testSimpleDoWhile() {
    doTest();
  }

  public void testSideEffectInIf() {
    doTest();
  }


  private void doTest() {
    myFixture.enableInspections(new ReturnSeparatedFromComputationInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
