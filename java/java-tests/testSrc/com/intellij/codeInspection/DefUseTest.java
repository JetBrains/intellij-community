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
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class DefUseTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/defUse";
  }

  public void testSCR5144() { doTest(); }
  public void testSCR6843() { doTest(); }
  public void testUnusedVariable() { doTest(); }
  public void testArrayIndexUsages() { doTest(); }
  public void testSCR28019() { doTest(); }
  public void testSCR40364() { doTest(); }
  public void testArrayLength() { doTest(); }
  public void testUsedInArrayInitializer() { doTest(); }
  public void testHang() { doTest(); }
  public void testOperatorAssignment() { doTest(); }
  public void testTryWithFinally() { doTest(); }
  public void testTryWithoutFinally() { doTest(); }

  public void testSequence() { doTest(); }
  public void testIfAfter() { doTest(); }
  public void testIfBefore() { doTest(); }
  public void testIfNested() { doTest(); }
  public void testInLoop() { doTest(); }
  public void testIfInLoop() { doTest(); }
  public void testThrowInTry() { doTest(); }
  public void testThrowInFinally() { doTest(); }
  public void testTryThrowFinally() { doTest(); }
  public void testNestedTryFinally() { doTest(); }
  public void testNestedBigTryFinally() { doTest(); }
  public void testTryWithFinallyRethrow() { doTest(); }
  public void testComplexDoubleTryFinally() { doTest(); }
  public void testComplexTripleTryFinally() { doTest(); }
  public void testComplexQuintipleTryFinally() { doTest(); }

  private void doTest() {
    myFixture.enableInspections(new DefUseInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}