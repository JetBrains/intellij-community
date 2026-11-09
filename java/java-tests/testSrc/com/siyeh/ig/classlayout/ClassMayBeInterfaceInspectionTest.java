// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
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
public class ClassMayBeInterfaceInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection());

  @Test
  public void testOne() {
    inspectionFixture.get().doTest("""
             abstract class /*Abstract class 'ConvertMe' may be interface*/ConvertMe/**/ {
                 public static final String S = "";
                 public void m() {}
                 public static void n() {
                     new ConvertMe() {};
                     class X extends ConvertMe {}
                 }
                 public class A {}
             }""");
  }

  @Test
  public void testOnTwo() {
    inspectionFixture.get().doTest("""
             class ConvertMe {
                 public static final String S = "";
                 public void m() {}
                 public static void n() {
                     new ConvertMe() {};
                     class X extends ConvertMe {}
                 }
                 public class A {}
             }""");
  }

  @Test
  public void testMethodCantBeDefault() {
    inspectionFixture.get().doTest("""
             class Issue {
                 public abstract class Inner {
                     public Issue getParent() {
                         return Issue.this;
                     }
                 }
             }""");
  }

  @Test
  public void testObjectMethods() {
    inspectionFixture.get().doTest("""
             abstract class X {
               public boolean equals(Object o) { return false; }
               public int hashCode() { return 1; }
               public String toString() { return null; }
             }""");
  }

  @Test
  public void testLocalClassAccessingParameter() {
    inspectionFixture.get().doTest("""
             class Outer {
               void x(int parameter) {
                 abstract class Example {
                   public static final int MY_CONST = 42;
                   public abstract void foo();
             
                   public void g() {
                     System.out.println(parameter);
                   }
                 }
             
                 class Inheritor extends Example {
                   @Override
                   public void foo() {
                     System.out.println(MY_CONST);
                   }
                 }
               }
             }
             """);
  }

  private static InspectionProfileEntry getInspection() {
    final ClassMayBeInterfaceInspection inspection = new ClassMayBeInterfaceInspection();
    inspection.reportClassesWithNonAbstractMethods = true;
    return inspection;
  }
}
