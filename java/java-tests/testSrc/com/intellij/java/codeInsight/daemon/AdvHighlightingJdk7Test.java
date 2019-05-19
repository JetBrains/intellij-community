/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AdvHighlightingJdk7Test extends LightCodeInsightFixtureTestCase {
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
    myFixture.addClass("package P1;\n" +
                       "public class C1 {\n" +
                       "    public static final int Foo;\n" +
                       "}\n");
    myFixture.addClass("package P1;\n" +
                       "public class C2 {\n" +
                       "    public static final int Foo;\n" +
                       "}\n");

    doTest();
  }

  public void testStaticImportMethodShadowing() {
    doTest();
  }

  //static import conflicts---------------
  private void setupBaseClasses() {
    myFixture.addClass("package x;\n" +
                       "\n" +
                       "public class Base1 {\n" +
                       "  public static final int F = 1;\n" +
                       "  public static void m(int i) { }\n" +
                       "  public static class F { }\n" +
                       "  public static class D { }\n" +
                       "  public interface I1 {\n" +
                       "    int IF = 1;\n" +
                       "  }\n" +
                       "  public interface I2 {\n" +
                       "    float IF = 2.0f;\n" +
                       "  }\n" +
                       "}");
    myFixture.addClass("package x;\n" +
                       "\n" +
                       "public class Base2 extends Base1 {\n" +
                       "  public static final float F = 2.0f;\n" +
                       "  public static void m(float f) { }\n" +
                       "  public static class F { }\n" +
                       "  public static class D { }\n" +
                       "  public interface II extends I1, I2 { }\n" +
                       "  public enum E { }\n" +
                       "}");
  }

  public void testStaticImportConflict() {
    setupBaseClasses();
    doTest();
  }

  public void testStaticOnDemandImportConflict() {
    setupBaseClasses();
    myFixture.addClass("package x;\n" +
                       "\n" +
                       "public class E { }");
    doTest();
  }

  public void testStaticAndSingleImportConflict() {
    setupBaseClasses();
    doTest();
  }

  //----------------------------------------

  public void testRawInnerClassImport() {
    myFixture.addClass("package p;\n" +
                       "import p2.GenericClass.InnerClass;\n" +
                       "public class Class2 {\n" +
                       "  public static boolean test(InnerClass context) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}\n");
    myFixture.addClass("package p2;\n" +
                       "public class GenericClass<T> {\n" +
                       "  public class InnerClass {\n" +
                       "  }\n" +
                       "}");
    doTest();
  }
  
  public void testRawInnerClassImportOnDemand() {
    myFixture.addClass("package p;\n" +
                       "import p2.GenericClass.*;\n" +
                       "public class Class2 {\n" +
                       "  public static boolean test(InnerClass context) {\n" +
                       "    return true;\n" +
                       "  }\n" +
                       "}\n");
    myFixture.addClass("package p2;\n" +
                       "public class GenericClass<T> {\n" +
                       "  public class InnerClass {\n" +
                       "  }\n" +
                       "}");
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
    myFixture.addClass("package pck;\n" +
                       "class Assert {\n" +
                       "  static void assertEquals(Object o1, Object o2) {}\n" +
                       "  static void assertEquals(long l1, long l2) {}\n" +
                       "}\n");
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
                               "package pck;\n" +
                               "public class A {\n" +
                               "    static void bar(Object a) {\n" +
                               "        System.out.println(\"A\");\n" +
                               "    }\n" +
                               "}\n" +
                               "class B {\n" +
                               "    static void bar(Object a) {\n" +
                               "        System.out.println(\"B\");\n" +
                               "    }\n" +
                               "}\n");
    doTest();
  }

  private void doTest() {
    final String name = getTestName(false);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, myFixture.getModule(), getTestRootDisposable());
    myFixture.configureByFile(name + ".java");
    myFixture.checkHighlighting(false, false, false);
  }
}
