// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bitwise;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class NegativeIntConstantInLongContextInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String TEST_DATA_PATH = "/ig/com/siyeh/igtest/bitwise/negative_int";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + TEST_DATA_PATH;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.enableInspections(new NegativeIntConstantInLongContextInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testNegativeIntConstant() {
    doTest();
  }

  public static class NegativeIntConstantInLongContextFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new NegativeIntConstantInLongContextInspection[]{new NegativeIntConstantInLongContextInspection()};
    }

    @Override
    protected @NotNull String getTestDataPath() {
      return JavaTestUtil.getJavaTestDataPath();
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_PATH;
    }
  }
}
