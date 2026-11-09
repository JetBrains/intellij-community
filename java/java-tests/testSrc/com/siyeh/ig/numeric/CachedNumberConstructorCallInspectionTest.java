// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class CachedNumberConstructorCallInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

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
