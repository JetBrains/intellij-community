// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.generate.inspection;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author Bas Leijdekkers
 */
public class ClassHasNoToStringMethodInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testBasic() {
    doTest("class <warning descr=\"Class 'X' does not override 'toString()' method\">X</warning> {" +
           "  private int i = 0;" +
           "}");
  }

  public void testDoNotWarnOnInnerClass() {
    doTest("class X {" +
           "  class Inner {" +
           "    private int i = 0;" +
           "  }" +
           "}");
  }

  public void testDoNotWarnOnRecord() {
    doTest("record R(int x, int y) {}");
  }

  private void doTest(@NonNls String text) {
    myFixture.configureByText("X.java", text);
    final ClassHasNoToStringMethodInspection inspection = new ClassHasNoToStringMethodInspection();
    inspection.excludeInnerClasses = true;
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(true, false, false);
  }
}
