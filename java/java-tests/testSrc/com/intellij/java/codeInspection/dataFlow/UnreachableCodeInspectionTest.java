// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.UnreachableCodeInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class UnreachableCodeInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_17_ANNOTATED);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/unreachableCode/");
  }

  @Test
  public void testSystemExit() { doTest(); }
  
  @Test
  public void testThrow() { doTest(); }
  
  @Test
  public void testTrivialReturn() { doTest(); }
  
  @Test
  public void testSimpleCatch() { doTest(); }
  
  @Test
  public void testSwitchFallthrough() { doTest(); }

  @Test
  public void testSwitchFail() { doTest(); }
  
  @Test
  public void testSwitchDefault() { doTest(); }
  
  @Test
  public void testInitializer() { doTest(); }
  
  @Test
  public void testConstructor() { doTest(); }
  
  @Test
  public void testCatchLinkageError() { doTest(); }
  
  @Test
  public void testLambdaCast() { doTest(); }
  
  @Test
  public void testFailingCast() { doTest(); }
  
  @Test
  public void testUnresolvedCast() { doTest(); }
  
  @Test
  public void testUnresolvedCall() { doTest(); }
  
  @Test
  public void testArrayIndexOutOfBounds() { doTest(); }
  
  @Test
  public void testCatchStringConcat() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.enableInspections(new UnreachableCodeInspection());
    myFixture.checkHighlighting();
  }
}
