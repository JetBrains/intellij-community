// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
@SuppressWarnings("CastToIncompatibleInterface")
public class CastToIncompatibleInterfaceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new CastToIncompatibleInterfaceInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("class X { " +
           "  I list = (/*Cast of expression with type 'C' to incompatible interface 'I'*/I/**/) new C(); " +
           "}" +
           "interface I {}" +
           "class C {}");
  }

  @Test
  public void testTooExpensiveToCheck() {
    inspectionFixture.get().doTest("import java.util.HashMap;" +
           "import java.util.List;" +
           "class X {" +
           "  List l = (List) new HashMap();" +
           "}");
  }

  /** @noinspection InstanceofIncompatibleInterface*/
  @Test
  public void testNullOrInstance() {
    inspectionFixture.get().doTest("class X { static I foo(X x) {if (x == null || x instanceof I) return (I)x;return null;}} interface I {}");
  }

  /** @noinspection InstanceofIncompatibleInterface*/
  @Test
  public void testOrNot() {
    inspectionFixture.get().doTest("""
             class A {
               public boolean check(final boolean flag) {
                 return flag || !(getDelegate() instanceof Bar) ||
                        Boolean.TRUE.equals(((Bar)getDelegate()).getValue());
               }
               native Foo getDelegate();
               static class Foo {}
               interface Bar { Boolean getValue();}
             }""");
  }

  @Test
  public void testCastToIncompatibleInterface() {
    inspectionFixture.get().doTest();
  }
}
