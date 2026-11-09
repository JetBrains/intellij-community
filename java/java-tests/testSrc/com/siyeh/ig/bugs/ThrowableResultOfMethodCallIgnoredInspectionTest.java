// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ThrowableResultOfMethodCallIgnoredInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ThrowableNotThrownInspection(), getEnvironmentClasses(),
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/bugs/throwable_result_of_method_call_ignored");

  @Test
  public void testThrowableResultOfMethodCallIgnored() {
    inspectionFixture.get().doTest();
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
package com.google.errorprone.annotations;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Target;@Target({METHOD, TYPE})
public @interface CanIgnoreReturnValue {}"""
    };
  }
}
