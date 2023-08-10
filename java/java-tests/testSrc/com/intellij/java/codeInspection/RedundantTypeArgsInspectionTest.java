// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.miscGenerics.RedundantTypeArgsInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class RedundantTypeArgsInspectionTest extends LightDaemonAnalyzerTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] { new RedundantTypeArgsInspection()};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  private void doTest() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_6, getModule(), getTestRootDisposable());
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
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_17, () -> doTest());
  }
}