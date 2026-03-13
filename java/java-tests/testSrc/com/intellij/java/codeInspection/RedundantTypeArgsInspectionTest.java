// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21;

public class RedundantTypeArgsInspectionTest extends LightDaemonAnalyzerTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] { new RedundantTypeArgsInspection()};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() {
    doTest("/inspection/redundantTypeArgs/" + getTestName(false) + ".java", true, false);
  }

  public void testReturnPrimitiveTypes() { // javac non-boxing: IDEA-53984
    doTest();
  }

  public void testConditionalExpression() {
    doTest();
  }

  public void testBoundInference() {
    doTest();
  }

  public void testNestedCalls() {
    doTest();
  }
  
  public void testTooManyArguments() {
    doTest();
  }

  public void testNonGeneric() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> doTest());
  }
}