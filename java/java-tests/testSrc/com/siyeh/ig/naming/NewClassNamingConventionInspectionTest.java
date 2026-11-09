// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.execution.junit.codeInspection.naming.TestClassNamingConvention;
import com.intellij.execution.junit.codeInspection.naming.TestSuiteNamingConvention;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class NewClassNamingConventionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY, null, NewClassNamingConventionInspection.class);

  @Test
  public void testSimple() { inspectionFixture.get().doTest(); }

  @Test
  public void testSuiteNameConventionTest() {
    inspectionFixture.get().addEnvironmentClass("package org.junit.runner;\n" +
                        "public @interface RunWith {Class<?> value();}");
    inspectionFixture.get().addEnvironmentClass("package org.junit.runners;\n" +
                        "public class Suite {}");
    inspectionFixture.get().addEnvironmentClass("package org.junit.runners;\n" +
                        "public class Parameterized extends Suite {}");
    inspectionFixture.get().addEnvironmentClass("package org.junit;\n" +
                        "public @interface Test{}");
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNested() {
    inspectionFixture.get().addEnvironmentClass("package org.junit.platform.commons.annotation; @interface Testable{}");
    inspectionFixture.get().addEnvironmentClass("package org.junit.jupiter.api; public @interface Nested{}");
    inspectionFixture.get().addEnvironmentClass("package org.junit.jupiter.api; import org.junit.platform.commons.annotation.Testable; @Testable public @interface Test{}");
    inspectionFixture.get().doTest();
  }

  private static InspectionProfileEntry getInspection() {
    NewClassNamingConventionInspection conventionInspection = new NewClassNamingConventionInspection();
    conventionInspection.setEnabled(true, ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
    conventionInspection.setEnabled(true, AbstractClassNamingConvention.ABSTRACT_CLASS_NAMING_CONVENTION_SHORT_NAME);
    conventionInspection.setEnabled(true, TestClassNamingConvention.TEST_CLASS_NAMING_CONVENTION_SHORT_NAME);
    conventionInspection.setEnabled(true, TestSuiteNamingConvention.TEST_SUITE_NAMING_CONVENTION_SHORT_NAME);
    return conventionInspection;
  }
}
