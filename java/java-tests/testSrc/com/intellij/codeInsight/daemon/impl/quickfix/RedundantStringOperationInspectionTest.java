// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.redundancy.RedundantStringOperationInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantStringOperationInspectionTest extends LightJavaInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantStringOperationInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_21;
  }

  public void testShouldReplaceStripByIsBlank() {doTest();}
  public void testEmptyStringArgument() {doTest();}
  public void testStringLengthArgument() {doTest();}
  public void testZeroArgument() {doTest();}
  public void testBAOStoString() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, this::doTest);
  }
  public void testNewStringNewChar() {doTest();}
  public void testStringValueOfNewChar() {doTest();}
  public void testRedundantStringOperation() {doTest();}

  public void testRedundantStrTemplateProcessorFix() {
    doQuickFixTest();
  }

  protected void doTest21() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21_PREVIEW, this::doTest);
  }

  protected void doQuickFixTest() {
    doTest21();
    checkQuickFixAll();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/redundantStringOperation/";
  }
}