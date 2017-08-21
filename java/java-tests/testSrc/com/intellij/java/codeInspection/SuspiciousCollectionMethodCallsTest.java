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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.SuspiciousCollectionsMethodCallsInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class SuspiciousCollectionMethodCallsTest extends LightCodeInsightFixtureTestCase {
  private SuspiciousCollectionsMethodCallsInspection myTool = new SuspiciousCollectionsMethodCallsInspection();

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/suspiciousCalls";
  }

  private void doTest() {
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testConcurrentHashMap() { doTest(); }
  public void testRemoveAllCall() { doTest(); }
  public void testSetList() { doTest(); }
  public void testUseDfa() { doTest(); }
  public void testWildcard() { doTest(); }
  public void testPolyConditionalExpressionPassedToMapGetCall() { doTest(); }
  public void testNewExpressionPassedToMapContains() { doTest(); }
  public void testIgnoreConvertible() {
    myTool.REPORT_CONVERTIBLE_METHOD_CALLS = false;
    doTest();
  }

  public void testNewMapMethods() {
    doTest();
  }

  public void testMethodReferenceWithCollectionCalls() {
    doTest();
  }

  public void testNonClassArgTypes() {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
