// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

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
@SuppressWarnings({"TextLabelInSwitchStatement", "UnusedLabel"})
public class TextLabelInSwitchStatementInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new TextLabelInSwitchStatementInspection(), getEnvironmentClasses());

  @Test
  public void testSwitchStatement() {
    inspectionFixture.get().doTest("""
             class Type {
                 void x(E e) {
                     switch (e) {
                         case A, B:
                             /*Text label 'caseZ:' in 'switch' statement*/caseZ/**/: break;
                         case C:
                             break;
                     }
                 }
             }""");
  }

  @Test
  public void testSwitchExpression() {
    inspectionFixture.get().doTest("""
             class Type {
                 void x(E e) {
                     int i = switch (e) {
                         case A,B:
                         /*Text label 'caseZ:' in 'switch' expression*/caseZ/**/: yield 1;
                         case C: yield 0;
                     };
                 }
             }""");
  }

  private static String[] getEnvironmentClasses() {
    return new String[] {
      """
enum E {
    A,B,C
}"""
    };
  }
}