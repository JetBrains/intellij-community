// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.redundancy.RedundantLengthCheckInspection;
import org.jetbrains.annotations.NotNull;

public class RedundantLengthCheckInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantLengthCheck/";
  }

  public void testRedundantLengthCheck() {
    doTest();
  }

  private void doTest() {
    myFixture.enableInspections(new RedundantLengthCheckInspection());
    myFixture.testHighlighting(true, false, true, getTestName(false) + ".java");
  }

}
