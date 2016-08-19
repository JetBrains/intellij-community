/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class Interface8MethodsHighlightingTest extends LightCodeInsightFixtureTestCase {
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
  public void testIDEA123839() { doTest(false, false); }
  public void testStaticOverloading() { doTest(false, false); }
  public void testDefaultSupersInStaticContext() {
    doTest(false, false);
  }
  public void testAnnotationTypeExtensionsNotSupported() {
    doTest(false, false);
  }

  public void testStaticMethodAccessibleThroughStaticImportButExplicitlyQualified() throws Exception {
    doTest(true, false);
  }

  public void testInheritanceOfStaticMethodFromDefault() throws Exception {
    doTest();
  }

  public void testUnrelatedDefaultsOverriddenWithConcreteMethodNonEmptySubstitutor() throws Exception {
    doTest(false, false);
  }

  public void testUnrelatedDefaultsWithTypeParameter() throws Exception {
    doTest(false, false);
  }

  public void testStaticMethodAccessibleBothThroughStaticImportAndInheritance() throws Exception {
    myFixture.addClass("package p; public interface Foo {" +
                       "    static void foo() {}" +
                       "    static void bar() {}" +
                       "}");
    myFixture.addClass("package p; public interface Boo {" +
                       "    static void boo() {}" +
                       "}");
    doTest(false, false);
  }

  public void testSuperProtectedCalls() throws Exception {
    myFixture.addClass("package p; public class Foo {" +
                       "  protected void foo(){}" +
                       "}");
    doTest();
  }

  public void testIDEA120498() { doTest(false, false); }

  public void testIgnoreStaticInterfaceMethods() throws Exception {
    doTest(true, false);
  }

  public void testAcceptStaticInterfaceMethodsImportedViaStaticImports() throws Exception {
    doTest();
  }

  public void testInherit2MethodsWithSameOverrideEquivalentSignatureFromOneSuperclass() throws Exception {
    doTest();
  }

  public void testMultipleDefaultsAndAbstractsSomeOfWhichOverridesEachOther() throws Exception {
    doTest();
  }

  public void testSubsignatureCheckWhen2DifferentMethodsBecomeOverrideEquivalent() throws Exception {
    doTest();
  }

  public void testUnrelatedDefaultsWhenAbstractIsOverridden() throws Exception {
    doTest();
  }

  public void testAbstractOverriddenBySecondDefault() throws Exception {
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
