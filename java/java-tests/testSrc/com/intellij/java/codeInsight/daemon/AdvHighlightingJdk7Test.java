// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class AdvHighlightingJdk7Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7/";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + BASE_PATH);
    myFixture.enableInspections(new DefUseInspection());
  }

  @Test
  public void testStaticImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package P1;
                         public class C1 {
                             public static final int Foo;
                         }
                         """);
    myFixture.addClass("""
                         package P1;
                         public class C2 {
                             public static final int Foo;
                         }
                         """);

    doTest();
  }

  @Test
  public void testStaticImportMethodShadowing() {
    doTest();
  }

  //static import conflicts---------------
  private void setupBaseClasses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package x;

                         public class Base1 {
                           public static final int F = 1;
                           public static void m(int i) { }
                           public static class F { }
                           public static class D { }
                           public interface I1 {
                             int IF = 1;
                           }
                           public interface I2 {
                             float IF = 2.0f;
                           }
                         }""");
    myFixture.addClass("""
                         package x;

                         public class Base2 extends Base1 {
                           public static final float F = 2.0f;
                           public static void m(float f) { }
                           public static class F { }
                           public static class D { }
                           public interface II extends I1, I2 { }
                           public enum E { }
                         }""");
  }

  @Test
  public void testStaticImportConflict() {
    setupBaseClasses();
    doTest();
  }

  @Test
  public void testStaticOnDemandImportConflict() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    setupBaseClasses();
    myFixture.addClass("""
                         package x;

                         public class E { }""");
    doTest();
  }

  @Test
  public void testStaticAndSingleImportConflict() {
    setupBaseClasses();
    doTest();
  }

  //----------------------------------------

  @Test
  public void testRawInnerClassImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package p;
                         import p2.GenericClass.InnerClass;
                         public class Class2 {
                           public static boolean test(InnerClass context) {
                             return true;
                           }
                         }
                         """);
    myFixture.addClass("""
                         package p2;
                         public class GenericClass<T> {
                           public class InnerClass {
                           }
                         }""");
    doTest();
  }

  @Test
  public void testRawInnerClassImportOnDemand() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package p;
                         import p2.GenericClass.*;
                         public class Class2 {
                           public static boolean test(InnerClass context) {
                             return true;
                           }
                         }
                         """);
    myFixture.addClass("""
                         package p2;
                         public class GenericClass<T> {
                           public class InnerClass {
                           }
                         }""");
    doTest();
  }

  @Test
  public void testAmbiguous() {
    doTest();
  }

  @Test
  public void testAmbiguousArrayInSubst() {
    doTest();
  }

  @Test
  public void testAmbiguousTypeParamExtends() {
    doTest();
  }

  @Test
  public void testAmbiguousTypeParamNmb() {
    doTest();
  }

  @Test
  public void testAmbiguousTypeParamNmb1() {
    doTest();
  }

  @Test
  public void testAmbiguousInheritance() {
    doTest();
  }

  @Test
  public void testAmbiguousInheritance1() {
    doTest();
  }

  @Test
  public void testAmbiguousVarargs() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    doTest();
  }

  @Test
  public void testAmbiguousVarargs1() {
    doTest();
  }

  @Test
  public void testAmbiguousMultiIntInheritance() {
    doTest();
  }

  @Test
  public void testAmbiguousMultipleTypeParamExtends() {
    doTest();
  }

  @Test
  public void testAmbiguousMultipleTypeParamExtends1() {
    doTest();
  }

  @Test
  public void testAmbiguousMultipleTypeParamExtends2() {
    doTest();
  }

  @Test
  public void testAmbiguousMultipleTypeParamExtends3() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57317() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57278() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57269() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67573() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57306() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67841() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57535() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67832() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67837() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA78027() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA25097() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA24768() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA21660() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA22547() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package pck;
                         class Assert {
                           static void assertEquals(Object o1, Object o2) {}
                           static void assertEquals(long l1, long l2) {}
                         }
                         """);
    doTest();
  }

  @Test
  public void testAmbiguousInferenceOrder() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA87672() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57500() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67864() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67836() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67576() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA67519() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57569() {
    doTest();
  }

  @Test
  public void testAmbiguousMethodsFromSameClassAccess() {
    doTest();
  }

  @Test
  public void testAmbiguousIDEA57633() {
    doTest();
  }

  @Test
  public void testAmbiguousStaticImportMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("pck/A.java",
                               """
                                 package pck;
                                 public class A {
                                     static void bar(Object a) {
                                         System.out.println("A");
                                     }
                                 }
                                 class B {
                                     static void bar(Object a) {
                                         System.out.println("B");
                                     }
                                 }
                                 """);
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = myFixture.getTestName(false);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, myFixture.getModule(), myFixture.getTestRootDisposable());
    myFixture.configureByFile(name + ".java");
    myFixture.checkHighlighting(false, false, false);
  }
}
