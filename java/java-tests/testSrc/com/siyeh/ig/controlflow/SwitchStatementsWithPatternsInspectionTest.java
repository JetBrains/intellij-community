// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class SwitchStatementsWithPatternsInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_23);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/controlflow/switch_statements_with_patterns");

  private final SwitchStatementsWithoutDefaultInspection myInspection = new SwitchStatementsWithoutDefaultInspection();

  @Test
  public void testIgnoreExhaustiveSwitchStatementsTrue() {
    myInspection.m_ignoreFullyCoveredEnums = true;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testIgnoreExhaustiveSwitchStatementsFalse() {
    myInspection.m_ignoreFullyCoveredEnums = false;
    inspectionFixture.get().doTest();
  }

  @Test
  public void testTrueFalsePatterns() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    String name = inspectionFixture.get().getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, true, true);
  }

  @Nullable
  private InspectionProfileEntry getInspection() {
    return myInspection;
  }
}