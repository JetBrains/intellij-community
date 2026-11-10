// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
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
public class Interface8MethodsHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/interfaceMethods";

  @Test
  public void testStaticMethod() { doTest(); }
  @Test
  public void testNotInheritFromUnrelatedDefault() { doTest(true, false); }
  @Test
  public void testDefaultMethodVisibility() { doTest(true, false); }
  @Test
  public void testInheritUnrelatedDefaults() { doTest(true, false); }
  @Test
  public void testExtensionMethods() { doTest(false, false); }
  @Test
  public void testInheritDefaultMethodInInterface() { doTest(false, false); }
  @Test
  public void testCheckForFunctionalInterfaceCandidatesWhenOverrideEquivalentMethodsAreFoundInSuperInterfaces() { doTest(false, false);}
  @Test
  public void testStaticMethodsInFunctionalInterface() { doTest(false, false); }
  @Test
  public void testCyclicSubstitutor() { doTest(false, false); }
  @Test
  public void testThisAccessibility() { doTest(false, false); }
  @Test
  public void testStaticMethodCalls() { doTest(false, false); }
  @Test
  public void testStaticMethodCallsAndOverloadResolution() { doTest(false, false); }
  @Test
  public void testDefaultMethodOverrideEquivalentObject() { doTest(false, false); }
  @Test
  public void testDefaultMethodOverrideAbstract() { doTest(false, false); }
  @Test
  public void testModifierNativeInInterface() { doTest(false, false); }
  @Test
  public void testStaticMethods() { doTest(false, false); }
  @Test
  public void testFinalStaticDefaultMethods() { doTest(false, false); }
  @Test
  public void testIDEA122720() { doTest(false, false); }
  @Test
  public void testInterfaceStaticMethodsWithSameErasure() { doTest(false, false); }
  @Test
  public void testIDEA123839() { doTest(false, false); }
  @Test
  public void testStaticOverloading() { doTest(false, false); }
  @Test
  public void testDefaultSupersInStaticContext() {
    doTest(false, false);
  }
  @Test
  public void testDefaultSupersInAnonymousContext() {
    doTest(false, false);
  }
  @Test
  public void testAnnotationTypeExtensionsNotSupported() {
    doTest(false, false);
  }
  @Test
  public void testStaticMethodAccessibleThroughStaticImportButExplicitlyQualified() { doTest(true, false); }
  @Test
  public void testStaticMethodOfInterfaceAccessibleThroughMethodReference() { doTest(true, false); }
  @Test
  public void testInheritanceOfStaticMethodFromDefault() {
    doTest();
  }

  @Test
  public void testUnrelatedDefaultsOverriddenWithConcreteMethodNonEmptySubstitutor() {
    doTest(false, false);
  }

  @Test
  public void testUnrelatedDefaultsWithTypeParameter() {
    doTest(false, false);
  }

  @Test
  public void testUnrelatedDefaultsWhenOneInterfaceOverrides2Unrelated() {
    doTest(false, false);
  }

  @Test
  public void testUnrelatedDefaultsWhenOverridingIsPresentExplicitly() {
    doTest(false, false);
  }

  @Test
  public void testIncompatibleReturnTypeWhenDefaultsHierarchyBroken() {
    doTest(false, false);
  }

  @Test
  public void testStaticMethodAccessibleBothThroughStaticImportAndInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p; public interface Foo {" +
                       "    static void foo() {}" +
                       "    static void bar() {}" +
                       "}");
    myFixture.addClass("package p; public interface Boo {" +
                       "    static void boo() {}" +
                       "}");
    doTest(false, false);
  }

  @Test
  public void testStaticMethodAccessibleThroughInheritance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p; public interface Foo {" +
                       "    static void foo() {}" +
                       "    interface FooEx extends Foo {} " +
                       "}");
    doTest(false, false);
  }

  @Test
  public void testSuperProtectedCalls() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package p; public class Foo {" +
                       "  protected void foo(){}" +
                       "}");
    doTest();
  }

  @Test
  public void testIDEA120498() { doTest(false, false); }

  @Test
  public void testIgnoreStaticInterfaceMethods() {
    doTest(true, false);
  }

  @Test
  public void testAcceptStaticInterfaceMethodsImportedViaStaticImports() {
    doTest();
  }

  @Test
  public void testInherit2MethodsWithSameOverrideEquivalentSignatureFromOneSuperclass() {
    doTest();
  }

  @Test
  public void testMultipleDefaultsAndAbstractsSomeOfWhichOverridesEachOther() {
    doTest();
  }

  @Test
  public void testSubsignatureCheckWhen2DifferentMethodsBecomeOverrideEquivalent() {
    doTest();
  }

  @Test
  public void testUnrelatedDefaultsWhenAbstractIsOverridden() {
    doTest();
  }

  @Test
  public void testAbstractOverriddenBySecondDefault() {
    doTest();
  }

  @Test
  public void testStaticAbstractDefaultInOneHierarchy() { doTest(); }

  @Test
  public void testMethodHierarchyWithDeclaredTypeParameters() {
    doTest();
  }

  @Test
  public void testInheritedStaticMethodOverrideAnotherInterface() {
    doTest();
  }

  @Test
  public void testAmbiguousStaticCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package sample;
                         interface Lambda0 {
                             void run();
                             static <T> Lambda0 lambda() {
                                 return () -> {};
                             }
                         }
                         """);
    myFixture.addClass("""
                  package sample;
                  interface Lambda1 extends Lambda0 {
                      static <E1 extends Exception> Lambda1 lambda() {
                          return ()->{};
                      }
                  }
                         """);
    doTest();
  }

  @Test
  public void testAmbiguousStaticCall2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package sample;
                         interface Lambda0 {
                             void run();
                             static <T> Lambda0 lambda() {
                                 return () -> {};
                             }
                         }
                         """);
    myFixture.addClass("""
                  package sample;
                  interface Lambda1 extends Lambda0 {
                      static <E1 extends Exception> Lambda1 lambda() {
                          return ()->{};
                      }
                  }
                         """);
    myFixture.addClass("""
                  package sample;
                  interface Lambda2 extends Lambda1 {}""");
    doTest();
  }
  private void doTest() {
    doTest(false, false);
  }

  private void doTest(boolean checkWarnings, boolean checkInfos) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String filePath = BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting(checkWarnings, checkInfos, false);
  }
}
