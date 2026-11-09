// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

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
@SuppressWarnings({"TextLabelInSwitchStatement", "UnusedLabel"})
public class TextLabelInSwitchStatementInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new TextLabelInSwitchStatementInspection(), getEnvironmentClasses());

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