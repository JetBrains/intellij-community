// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.valuebased;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.valuebased.SynchronizeOnValueBasedClassInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnValueBasedClassInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  static final @NonNls String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/valuebased";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final String abstractValueBased = BASE_PATH + "/classes/AbstractValueBased.java";
    final String ivalueBased = BASE_PATH + "/classes/IValueBased.java";
    final String openValueBased = BASE_PATH + "/classes/OpenValueBased.java";

    myFixture.configureByFile(abstractValueBased);
    myFixture.configureByFile(ivalueBased);
    myFixture.configureByFile(openValueBased);
    myFixture.enableInspections(new SynchronizeOnValueBasedClassInspection());
  }

  public void testExtendValueBasedClass() { doTest(); }
  public void testUseValueBasedInstanceAsMonitor() { doTest(); }
  public void testImplementValueBasedInterface() { doTest(); }
  public void testComplexValueBasedHierarchy() { doTest(); }

  private void doTest() {
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }
}
