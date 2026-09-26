// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;


public class LocalCanBeFinalTest extends LightJavaCodeInsightFixtureTestCase {
  private LocalCanBeFinal myTool;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/localCanBeFinal/";
  }
  
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTool = new LocalCanBeFinal();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    // has to have JFrame and sources
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  private void doTest() {
    myFixture.enableInspections(myTool);
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  public void testMultiWriteNoRead() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testUnreachableModification() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testIfTest() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSwitchBranches() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testIncompleteAssignment() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  
  public void testLambdaParameters() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = false;
    doTest();
  }

  public void testParameters() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_1() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_2() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_3() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_4() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_5() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_6() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7601() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7428_1() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7428() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR11757() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testTestFinal2() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testLambdaBody() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testLambdaBody2() {
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testForeachNotReported() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = false;
    myTool.REPORT_FOREACH_PARAMETERS = false;
    doTest();
  }

  public void testNestedForeach() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    myTool.REPORT_FOREACH_PARAMETERS = true;
    doTest();
  }

  public void testFor() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testCatchParameter() {
    myTool.REPORT_PARAMETERS = false;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }


  public void testRecord() {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testResource() {
    doTest();
  }

  public void testPatternVariables() {
    myTool.REPORT_PATTERN_VARIABLES = true;
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest);
  }
  
  public void testUnnamedVariables() {
    myTool.REPORT_PATTERN_VARIABLES = true;
    myTool.REPORT_VARIABLES = true;
    myTool.REPORT_FOREACH_PARAMETERS = true;
    myTool.REPORT_IMPLICIT_FINALS = true;
    myTool.REPORT_CATCH_PARAMETERS = true;
    doTest();
  }
}
