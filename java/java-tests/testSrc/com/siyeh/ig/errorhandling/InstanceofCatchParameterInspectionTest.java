// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

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
public class InstanceofCatchParameterInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new InstanceofCatchParameterInspection(), getEnvironmentClasses());

  @Test
  public void testSimple() {
    inspectionFixture.get().doStatementTest("try {" +
                    "} catch (Exception e) {" +
                    "  if ((/*'instanceof' on 'catch' parameter 'e'*/e/**/) instanceof NullPointerException) {}" +
                    "}");
  }

  @Test
  public void testNoWarn() {
    inspectionFixture.get().doStatementTest("try {" +
                    "} catch (RuntimeException e) {" +
                    "  if (e instanceof ControlFlowException) {" +
                    "    return;" +
                    "  }" +
                    "}");
  }

  private static String[] getEnvironmentClasses() {
    //noinspection NonExceptionNameEndsWithException
    return new String[] {
      "public interface ControlFlowException {}"
    };
  }
}