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

  private void doTest() throws Exception {
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  @Override
  protected void tearDown() throws Exception {
    myTool = null;
    super.tearDown();
  }

  public void testConcurrentHashMap() throws Exception { doTest(); }
  public void testRemoveAllCall() throws Exception { doTest(); }
  public void testSetList() throws Exception { doTest(); }
  public void testUseDfa() throws Exception { doTest(); }
  public void testWildcard() throws Exception { doTest(); }
  public void testPolyConditionalExpressionPassedToMapGetCall() throws Exception { doTest(); }
  public void testNewExpressionPassedToMapContains() throws Exception { doTest(); }
  public void testIgnoreConvertible() throws Exception {
    myTool.REPORT_CONVERTIBLE_METHOD_CALLS = false;
    doTest();
  }

  public void testNewMapMethods() throws Exception {
    doTest();
  }

  public void testMethodReferenceWithCollectionCalls() throws Exception {
    doTest();
  }

  public void testNonClassArgTypes() throws Exception {
    doTest();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
