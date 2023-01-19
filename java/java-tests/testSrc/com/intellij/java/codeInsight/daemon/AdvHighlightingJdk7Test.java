// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AdvHighlightingJdk7Test extends LightJavaCodeInsightFixtureTestCase {
  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/advHighlighting7/";


  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new DefUseInspection());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_7;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  public void testStaticImports() {
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

  public void testStaticImportMethodShadowing() {
    doTest();
  }

  //static import conflicts---------------
  private void setupBaseClasses() {
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

  public void testStaticImportConflict() {
    setupBaseClasses();
    doTest();
  }

  public void testStaticOnDemandImportConflict() {
    setupBaseClasses();
    myFixture.addClass("""
                         package x;

                         public class E { }""");
    doTest();
  }

  public void testStaticAndSingleImportConflict() {
    setupBaseClasses();
    doTest();
  }

  //----------------------------------------

  public void testRawInnerClassImport() {
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

  public void testRawInnerClassImportOnDemand() {
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

  public void testAmbiguous() {
    doTest();
  }

  public void testAmbiguousArrayInSubst() {
    doTest();
  }

  public void testAmbiguousTypeParamExtends() {
    doTest();
  }

  public void testAmbiguousTypeParamNmb() {
    doTest();
  }

  public void testAmbiguousTypeParamNmb1() {
    doTest();
  }

  public void testAmbiguousInheritance() {
    doTest();
  }

  public void testAmbiguousInheritance1() {
    doTest();
  }

  public void testAmbiguousVarargs() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), getTestRootDisposable());
    doTest();
  }

  public void testAmbiguousVarargs1() {
    doTest();
  }

  public void testAmbiguousMultiIntInheritance() {
    doTest();
  }

  public void testAmbiguousMultipleTypeParamExtends() {
    doTest();
  }

  public void testAmbiguousMultipleTypeParamExtends1() {
    doTest();
  }

  public void testAmbiguousMultipleTypeParamExtends2() {
    doTest();
  }

  public void testAmbiguousMultipleTypeParamExtends3() {
    doTest();
  }

  public void testAmbiguousIDEA57317() {
    doTest();
  }

  public void testAmbiguousIDEA57278() {
    doTest();
  }

  public void testAmbiguousIDEA57269() {
    doTest();
  }

  public void testAmbiguousIDEA67573() {
    doTest();
  }

  public void testAmbiguousIDEA57306() {
    doTest();
  }

  public void testAmbiguousIDEA67841() {
    doTest();
  }

  public void testAmbiguousIDEA57535() {
    doTest();
  }

  public void testAmbiguousIDEA67832() {
    doTest();
  }

  public void testAmbiguousIDEA67837() {
    doTest();
  }

  public void testAmbiguousIDEA78027() {
    doTest();
  }

  public void testAmbiguousIDEA25097() {
    doTest();
  }

  public void testAmbiguousIDEA24768() {
    doTest();
  }

  public void testAmbiguousIDEA21660() {
    doTest();
  }

  public void testAmbiguousIDEA22547() {
    myFixture.addClass("""
                         package pck;
                         class Assert {
                           static void assertEquals(Object o1, Object o2) {}
                           static void assertEquals(long l1, long l2) {}
                         }
                         """);
    doTest();
  }

  public void testAmbiguousInferenceOrder() {
    doTest();
  }

  public void testAmbiguousIDEA87672() {
    doTest();
  }

  public void testAmbiguousIDEA57500() {
    doTest();
  }

  public void testAmbiguousIDEA67864() {
    doTest();
  }

  public void testAmbiguousIDEA67836() {
    doTest();
  }

  public void testAmbiguousIDEA67576() {
    doTest();
  }

  public void testAmbiguousIDEA67519() {
    doTest();
  }

  public void testAmbiguousIDEA57569() {
    doTest();
  }

  public void testAmbiguousMethodsFromSameClassAccess() {
    doTest();
  }

  public void testAmbiguousIDEA57633() {
    doTest();
  }

  public void testAmbiguousStaticImportMethod() {
    myFixture.addFileToProject("pck\\A.java",
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
    final String name = getTestName(false);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, myFixture.getModule(), getTestRootDisposable());
    myFixture.configureByFile(name + ".java");
    myFixture.checkHighlighting(false, false, false);
  }
}
