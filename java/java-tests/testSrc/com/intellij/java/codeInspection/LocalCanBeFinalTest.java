// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
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
public class LocalCanBeFinalTest {
  // has to have JFrame and sources
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private LocalCanBeFinal myTool;
  
  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/localCanBeFinal/");
    myTool = new LocalCanBeFinal();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testMultiWriteNoRead() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testUnreachableModification() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testIfTest() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSwitchBranches() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testIncompleteAssignment() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  
  @Test
  public void testLambdaParameters() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = false;
    doTest();
  }

  @Test
  public void testParameters() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR6744_1() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR6744_2() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR6744_3() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR6744_4() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR6744_5() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR6744_6() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR7601() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR7428_1() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR7428() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testSCR11757() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  @Test
  public void testTestFinal2() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  @Test
  public void testLambdaBody() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  @Test
  public void testLambdaBody2() {
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  @Test
  public void testForeachNotReported() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = false;
    myTool.REPORT_FOREACH_PARAMETERS = false;
    doTest();
  }

  @Test
  public void testNestedForeach() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    myTool.REPORT_FOREACH_PARAMETERS = true;
    doTest();
  }

  @Test
  public void testFor() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  @Test
  public void testCatchParameter() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  @Test
  public void testRecord() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  @Test
  public void testResource() {
    doTest();
  }

  @Test
  public void testPatternVariables() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myTool.REPORT_PATTERN_VARIABLES = true;
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, this::doTest);
  }
  
  @Test
  public void testUnnamedVariables() {
    myTool.REPORT_PATTERN_VARIABLES = true;
    myTool.REPORT_VARIABLES = true;
    myTool.REPORT_FOREACH_PARAMETERS = true;
    myTool.REPORT_IMPLICIT_FINALS = true;
    myTool.REPORT_CATCH_PARAMETERS = true;
    doTest();
  }
}
