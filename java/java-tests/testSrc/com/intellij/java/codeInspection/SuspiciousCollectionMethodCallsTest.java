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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class SuspiciousCollectionMethodCallsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/suspiciousCalls");
  }

  private SuspiciousCollectionsMethodCallsInspection myTool = new SuspiciousCollectionsMethodCallsInspection();

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @AfterEach
  void tearDown() {
    myTool = null;
  }

  @Test
  public void testConcurrentHashMapCalls() { doTest(); }
  @Test
  public void testRemoveAllCall() { doTest(); }
  @Test
  public void testRetainAllCall() { doTest(); }
  @Test
  public void testSetList() { doTest(); }
  @Test
  public void testUseDfa() { doTest(); }
  @Test
  public void testWildcard() { doTest(); }
  @Test
  public void testGuava() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.google.common.cache;" +
                "public interface Cache<K, V> {V getIfPresent(Object k);void invalidate(Object k);}");
    doTest();
  }
  @Test
  public void testPolyConditionalExpressionPassedToMapGetCall() { doTest(); }
  @Test
  public void testNewExpressionPassedToMapContains() { doTest(); }
  @Test
  public void testIgnoreConvertible() {
    myTool.REPORT_CONVERTIBLE_METHOD_CALLS = false;
    doTest();
  }

  @Test
  public void testNewMapMethods() {
    doTest();
  }

  @Test
  public void testMethodReferenceWithCollectionCalls() { doTest(); }
  @Test
  public void testSuspiciousSecondArg() { doTest(); }

  @Test
  public void testNonClassArgTypes() {
    doTest();
  }
}
