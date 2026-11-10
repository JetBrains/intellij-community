// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.generate.inspection;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.java.generate.inspection.ClassHasNoToStringMethodInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ClassHasNoToStringMethodInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testBasic() {
    doTest("class <warning descr=\"Class 'X' does not override 'toString()' method\">X</warning> {" +
           "  private int i = 0;" +
           "}");
  }

  @Test
  public void testDoNotWarnOnInnerClass() {
    doTest("class X {" +
           "  class Inner {" +
           "    private int i = 0;" +
           "  }" +
           "}");
  }

  @Test
  public void testDoNotWarnOnRecord() {
    doTest("record R(int x, int y) {}");
  }

  private void doTest(@NonNls String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("X.java", text);
    final ClassHasNoToStringMethodInspection inspection = new ClassHasNoToStringMethodInspection();
    inspection.excludeInnerClasses = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, false);
  }
}
