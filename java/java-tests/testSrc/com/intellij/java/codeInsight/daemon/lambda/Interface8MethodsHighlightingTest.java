// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class Interface8MethodsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/interfaceMethods";

  public void testStaticMethod() { doTest(); }
  public void testNotInheritFromUnrelatedDefault() { doTest(true, false); }
  public void testDefaultMethodVisibility() { doTest(true, false); }
  public void testInheritUnrelatedDefaults() { doTest(true, false); }
  public void testExtensionMethods() { doTest(false, false); }
  public void testInheritDefaultMethodInInterface() { doTest(false, false); }
  public void testCheckForFunctionalInterfaceCandidatesWhenOverrideEquivalentMethodsAreFoundInSuperInterfaces() { doTest(false, false);}
  public void testStaticMethodsInFunctionalInterface() { doTest(false, false); }
  public void testCyclicSubstitutor() { doTest(false, false); }
  public void testThisAccessibility() { doTest(false, false); }
  public void testStaticMethodCalls() { doTest(false, false); }
  public void testStaticMethodCallsAndOverloadResolution() { doTest(false, false); }
  public void testDefaultMethodOverrideEquivalentObject() { doTest(false, false); }
  public void testDefaultMethodOverrideAbstract() { doTest(false, false); }
  public void testModifierNativeInInterface() { doTest(false, false); }
  public void testStaticMethods() { doTest(false, false); }
  public void testFinalStaticDefaultMethods() { doTest(false, false); }
  public void testIDEA122720() { doTest(false, false); }
  public void testInterfaceStaticMethodsWithSameErasure() { doTest(false, false); }
  public void testIDEA123839() { doTest(false, false); }
  public void testStaticOverloading() { doTest(false, false); }
  public void testDefaultSupersInStaticContext() {
    doTest(false, false);
  }
  public void testDefaultSupersInAnonymousContext() {
    doTest(false, false);
  }
  public void testAnnotationTypeExtensionsNotSupported() {
    doTest(false, false);
  }
  public void testStaticMethodAccessibleThroughStaticImportButExplicitlyQualified() { doTest(true, false); }
  public void testStaticMethodOfInterfaceAccessibleThroughMethodReference() { doTest(true, false); }
  public void testInheritanceOfStaticMethodFromDefault() {
    doTest();
  }

  public void testUnrelatedDefaultsOverriddenWithConcreteMethodNonEmptySubstitutor() {
    doTest(false, false);
  }

  public void testUnrelatedDefaultsWithTypeParameter() {
    doTest(false, false);
  }

  public void testUnrelatedDefaultsWhenOneInterfaceOverrides2Unrelated() {
    doTest(false, false);
  }

  public void testUnrelatedDefaultsWhenOverridingIsPresentExplicitly() {
    doTest(false, false);
  }

  public void testIncompatibleReturnTypeWhenDefaultsHierarchyBroken() {
    doTest(false, false);
  }

  public void testStaticMethodAccessibleBothThroughStaticImportAndInheritance() {
    myFixture.addClass("package p; public interface Foo {" +
                       "    static void foo() {}" +
                       "    static void bar() {}" +
                       "}");
    myFixture.addClass("package p; public interface Boo {" +
                       "    static void boo() {}" +
                       "}");
    doTest(false, false);
  }

  public void testStaticMethodAccessibleThroughInheritance() {
    myFixture.addClass("package p; public interface Foo {" +
                       "    static void foo() {}" +
                       "    interface FooEx extends Foo {} " +
                       "}");
    doTest(false, false);
  }

  public void testSuperProtectedCalls() {
    myFixture.addClass("package p; public class Foo {" +
                       "  protected void foo(){}" +
                       "}");
    doTest();
  }

  public void testIDEA120498() { doTest(false, false); }

  public void testIgnoreStaticInterfaceMethods() {
    doTest(true, false);
  }

  public void testAcceptStaticInterfaceMethodsImportedViaStaticImports() {
    doTest();
  }

  public void testInherit2MethodsWithSameOverrideEquivalentSignatureFromOneSuperclass() {
    doTest();
  }

  public void testMultipleDefaultsAndAbstractsSomeOfWhichOverridesEachOther() {
    doTest();
  }

  public void testSubsignatureCheckWhen2DifferentMethodsBecomeOverrideEquivalent() {
    doTest();
  }

  public void testUnrelatedDefaultsWhenAbstractIsOverridden() {
    doTest();
  }

  public void testAbstractOverriddenBySecondDefault() {
    doTest();
  }

  public void testStaticAbstractDefaultInOneHierarchy() { doTest(); }

  public void testMethodHierarchyWithDeclaredTypeParameters() {
    doTest();
  }

  public void testInheritedStaticMethodOverrideAnotherInterface() {
    doTest();
  }

  public void testAmbiguousStaticCall() {
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

  public void testAmbiguousStaticCall2() {
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
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    myFixture.configureByFile(filePath);
    myFixture.checkHighlighting(checkWarnings, checkInfos, false);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
