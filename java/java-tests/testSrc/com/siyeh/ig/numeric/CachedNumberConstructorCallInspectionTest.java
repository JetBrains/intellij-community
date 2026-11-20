// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@AnalysisHostTest
public class CachedNumberConstructorCallInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> getInspection());

  private static InspectionProfileEntry getInspection() {
    final CachedNumberConstructorCallInspection inspection = new CachedNumberConstructorCallInspection();
    inspection.reportOnlyWhenDeprecated = false;
    return inspection;
  }

  @Test
  public void testSimple() { inspectionFixture.get().doStatementTest("new /*Number constructor call with primitive argument*/Integer/**/(1);"); }
  @Test
  public void testStringArgument() { inspectionFixture.get().doStatementTest("new /*Number constructor call with primitive argument*/Byte/**/(\"1\");"); }
  @Test
  public void testNoWarn() { inspectionFixture.get().doStatementTest("Long.valueOf(1L);"); }
  @Test
  public void testNoAssertionError() { inspectionFixture.get().doStatementTest("Integer i = new Integer(new String/*!'(' or '[' expected*//*!',' or ')' expected*/{/*!*//*!*/}/*!';' expected*//*!Unexpected token*/)/*!*//*!*/;"); }
}
