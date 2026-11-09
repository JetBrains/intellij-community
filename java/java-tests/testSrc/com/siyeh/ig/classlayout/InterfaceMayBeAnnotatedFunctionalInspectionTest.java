// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
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
public class InterfaceMayBeAnnotatedFunctionalInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new InterfaceMayBeAnnotatedFunctionalInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("interface /*Interface 'Simple' may be annotated with @FunctionalInterface*/Simple/**/ {" +
           "  void f();" +
           "}");
  }

  @Test
  public void testLessSimple() {
    inspectionFixture.get().doTest("""
             interface /*Interface 'F' may be annotated with @FunctionalInterface*/F/**/  {    boolean equals(Object o);    static void f00() {}
                 default void g() {}
                 void f();
             }""");
  }

  @Test
  public void testNotFunctional() {
    inspectionFixture.get().doTest("interface G {" +
           "<T> void f();" +
           "}");
  }

  @Test
  public void testAlreadyAnnotated() {
    inspectionFixture.get().doTest("@FunctionalInterface interface Asd {" +
           "    boolean doSmth();" +
           "}");
  }

  @Test
  public void testSealed() {
    inspectionFixture.get().doTest("""
             sealed interface Fooy {
                 String foo();
             }

             final class X implements Fooy {
                 @Override
                 public String foo() {
                     return null;
                 }
             }""");
  }

  @Test
  public void testAnnotationType() {
    inspectionFixture.get().doTest("@interface A {}");
  }
}
