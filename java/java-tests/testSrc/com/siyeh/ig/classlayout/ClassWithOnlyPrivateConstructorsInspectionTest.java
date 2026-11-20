// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;

@AnalysisHostTest
public class ClassWithOnlyPrivateConstructorsInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture();
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new ClassWithOnlyPrivateConstructorsInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
             class /*Class 'X' with only 'private' constructors should be declared 'final'*/X/**/ {
               private X() {}
               private X(int i) {}
             }""");
  }

  @Test
  public void testExtendingInnerClass() {
    inspectionFixture.get().doTest("""
             class X {
               private X() {}
               class Y {
                 class Z extends X{}
               }
             }""");
  }

  @Test
  public void testNoConstructors() {
    inspectionFixture.get().doTest("class X {}");
  }

  @Test
  public void testNoWarnOnFinalClass() {
    inspectionFixture.get().doTest("""
             final class X {
               private X() {}
             }""");
  }

  @Test
  public void testNoWarnOnAnonymInheritor() {
    //noinspection ResultOfObjectAllocationIgnored
    inspectionFixture.get().doTest("""
             class X {
               private X() {}
               static {new X() {};}
             }""");
  }

  @Test
  public void testEnum() {
    //noinspection UnnecessaryEnumModifier
    inspectionFixture.get().doTest("""
             enum Currencies {
                 EURO, DOLLAR;
                 private Currencies() {
                 }
             }""");
  }

  @Test
  public void testPublicConstructor() {
    //noinspection PublicConstructorInNonPublicClass
    inspectionFixture.get().doTest("""
             class A {
               public A() {}
             }""");
  }

  @Test
  public void testSubclassInSameFile() {
    inspectionFixture.get().doTest("""
             class Test {
                 private static class Inner {
                     private Inner() {}
                 }
                 private static class InnerSub extends Inner {}
             }""");
  }
}