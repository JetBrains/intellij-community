// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

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
public class InstanceofCatchParameterInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new InstanceofCatchParameterInspection(), getEnvironmentClasses());

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