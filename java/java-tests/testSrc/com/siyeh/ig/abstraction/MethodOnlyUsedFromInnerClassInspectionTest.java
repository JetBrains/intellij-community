// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
@SuppressWarnings("MethodMayBeStatic")
public class MethodOnlyUsedFromInnerClassInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_15);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new MethodOnlyUsedFromInnerClassInspection());

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest("""
             class X {
               private void /*Method 'foo()' is only used from inner class 'Inner'*/foo/**/() {}
              \s
               class Inner {
                 void test() {foo();}
               }
             }""");
  }

  @Test
  public void testAnonymous() {
    inspectionFixture.get().doTest("""
             class X {
               private int /*Method 'foo()' is only used from an anonymous class derived from 'Inner'*/foo/**/() {return 5;}
              \s
               void test() {
                 new Inner(1) {
                   int x = foo();
                 };
               }
              \s
               static class Inner { Inner(int x) {}}
             }""");
  }

  @Test
  public void testAnonymousCtor() {
    inspectionFixture.get().doTest("""
             class X {
               private int foo() {return 5;}
              \s
               void test() {
                 new Inner(foo()) {
                   int x = 5;
                 };
               }
              \s
               static class Inner { Inner(int x) {}}
             }""");
  }

  @Test
  public void testLocalClass() {
    inspectionFixture.get().doTest("""
             class X {
               private void /*Method 'foo()' is only used from local class 'Local'*/foo/**/() {}
              \s
                void x() {
                 class Local {
                   void test() {foo();}
                 }
               }
             }""");
  }

  @Test
  public void testNoWarnLocalClass() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    final MethodOnlyUsedFromInnerClassInspection inspection = new MethodOnlyUsedFromInnerClassInspection();
    inspection.ignoreMethodsAccessedFromAnonymousClass = true;
    myFixture.enableInspections(inspection);
    inspectionFixture.get().doTest("""
             class X {
               private void foo() {}
              \s
                void x() {
                 class Local {
                   void test() {foo();}
                 }
               }
             }""");
  }

  @Test
  @SuppressWarnings({"PublicConstructorInNonPublicClass", "Convert2Lambda"})
  public void testIgnoreUsedOutsideInnerClass() {
    inspectionFixture.get().doTest("""
             class OnlyUsedFromAnonymousWarning {

             \t public OnlyUsedFromAnonymousWarning() {
             \t\tRunnable someInterface = new Runnable() {
             \t\t\t@Override
             \t\t\tpublic void run() {
             \t\t\t\tproblemMethod();
             \t\t\t}
             \t\t};
             \t\tproblemMethod();
             \t}

             \tprivate static void problemMethod() {
             \t\tSystem.out.println("Is used not only from anonymous class!");
             \t}
             }""");
  }

  @Test
  public void testIgnoreStaticMethodCalledFromNonStaticInnerClass() {
    inspectionFixture.get().doTest("""
             class Outer {
                 public static void main(String[] args) {
                     class Local {
                         void run(String arg) {
                             if (!isEmpty(arg)) {
                                 System.out.println("Argument is supplied");
                             }
                         }
                     }
                     new Local().run(args[0]);
                 }
                 private static boolean isEmpty(String s) {
                     return s != null && s.trim().isEmpty();
                 }
             }""");
  }
}
