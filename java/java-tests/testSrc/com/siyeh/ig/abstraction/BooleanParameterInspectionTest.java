// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.abstraction;

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
public class BooleanParameterInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new BooleanParameterInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class X {" +
           "  public void /*'public' method 'm()' with 'boolean' parameter*/m/**/(boolean b) {}" +
           "  public void n() {}" +
           "  public void o(int i) {}" +
           "  void p(boolean b) {}" +
           "}");
  }

  @Test
  public void testConstructor() {
    inspectionFixture.get().doTest("class X {" +
           "  public /*'public' constructor 'X()' with 'boolean' parameter*/X/**/(boolean x) {}" +
           "}");
  }

  @Test
  public void testSetter() {
    inspectionFixture.get().doTest("class X {" +
           "  public void setMyProperty(final boolean myProperty) {}" +
           "}");
  }

  @Test
  public void testVarargs() {
    inspectionFixture.get().doTest("class Y {" +
           "    public Y(boolean... b) {}" +
           "}" +
           "class X extends Y {" +
           "    public /*'public' constructor 'X()' with 'boolean' parameter*/X/**/(boolean b) {" +
           "        super(true, b);" +
           "    }" +
           "}");
  }

  @Test
  public void testInterfaceMethods() {
    inspectionFixture.get().doTest(
      """
          interface I {
            private boolean check(boolean b) { return !b; }
            private static boolean check2(boolean b) { return !b; }
            boolean /*'public' method 'check3()' with 'boolean' parameter*/check3/**/(boolean b);
          }
          """
    );
  }
}
