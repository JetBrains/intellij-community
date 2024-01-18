// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection.dataFlow;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.dataFlow.UnreachableCodeInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class UnreachableCodeInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/dataFlow/unreachableCode/";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17_ANNOTATED;
  }

  public void testSystemExit() { doTest(); }
  
  public void testThrow() { doTest(); }
  
  public void testTrivialReturn() { doTest(); }
  
  public void testSimpleCatch() { doTest(); }
  
  public void testSwitchFallthrough() { doTest(); }

  public void testSwitchFail() { doTest(); }
  
  public void testSwitchDefault() { doTest(); }
  
  public void testInitializer() { doTest(); }
  
  public void testConstructor() { doTest(); }
  
  public void testCatchLinkageError() { doTest(); }
  
  public void testLambdaCast() { doTest(); }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.enableInspections(new UnreachableCodeInspection());
    myFixture.checkHighlighting();
  }
}
