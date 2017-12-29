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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.util.ArrayUtil;

/**
 * @author Konstantin Bulenkov
 */
public class JavaReflectionCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/reflection/";
  }

  public void testField() {
    doTest(1, "num", "num2", "num3");
  }

  public void testDeclaredField() {
    doTest(2, "num", "num1", "num2", "num0");
  }

  public void testDeclaredMethod() {
    doTest(3, "method", "method0", "method1", "method2");
  }

  public void testDeclaredMethod2() {
    doTest(1, "method", "method1", "method2");
  }

  public void testMethod() {
    doTestFirst(1, "method", "method2", "method3");
  }

  public void testForNameDeclaredMethod() {
    doTest(2, "method", "method1", "method2");
  }

  public void testForNameMethod() {
    doTestFirst(1, "method", "method2", "method3");
  }

  public void testForNameField() {
    doTest(1, "num", "num2", "num3");
  }

  public void testForNameDeclaredField() {
    doTest(1, "num", "num1", "num2");
  }

  public void testVarargMethod() {
    doTest(0, "vararg", "vararg2");
  }

  public void testGenerics() {
    myFixture.addFileToProject("a.properties", "foo=bar"); // check that property variants don't override reflection ones
    doTestFirst(0, "foo");
  }

  public void testInheritedMethod() {
    doTestFirst(1, "method", "method2", "method3", "method5");
  }

  public void testInheritedDeclaredMethod() {
    doTest(1, "method", "method3", "method5");
  }

  public void testInheritedField() {
    doTest(1, "num", "num2", "num3", "num5");
  }

  public void testInheritedDeclaredField() {
    doTest(1, "num", "num3", "num5");
  }

  public void testShadowedField() {
    doTest(0, "shadowed");
  }

  public void testShadowedSuperField() {
    doTest(0, "shadowed");
  }

  public void testInitRaw() {
    doTestFirst(1, "method", "method2");
  }

  public void testInitWithType() {
    doTestFirst(1, "method", "method2");
  }

  public void testInitChain() {
    doTest(1, "num", "num2");
  }

  public void testAssignChain() {
    doTest(1, "num", "num2");
  }

  public void testAssignCycle() {
    doTest(-1); // check that the recursion guard breaks the cycle
  }

  public void testCallChain() {
    doTest(0, "num");
  }

  public void testJdk14() {
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_4, () -> doTestFirst(0, "method"));
  }

  public void testWildcard() {
    doTestFirst(0, "method");
  }

  public void testConstantMethod() {
    doTestFirst(0, "method", "method2");
  }

  public void testDistantDefinition() {
    doTest(1, "method", "method2");
  }

  public void testVariableGetClassField() {
    doTest(1, "num", "num2", "num3");
  }

  public void testConstantGetClassField() {
    doTest(2, "num", "num3", "num2");
  }

  public void testExpressionGetClassField() {
    doTest(1, "num", "num2", "num3");
  }


  public void testClassForNameClasses() {
    myFixture.addClass("package foo.bar; public class PublicClass {}");
    myFixture.addClass("package foo.bar; class PackageLocalClass {}");
    doTest(1, "PackageLocalClass", "PublicClass");
  }

  public void testClassForNamePackages() {
    myFixture.addClass("package foo.bar.one; public class FirstClass {}");
    myFixture.addClass("package foo.bar.two; public class SecondClass {}");
    doTest(0, "one", "two");
  }

  public void testClassForNameNestedAutocomplete() {
    myFixture.addClass("package foo.bar; public class PublicClass { public static class NestedClass {} }");
    doTest(-1, () -> assertNull("Auto-completed", myFixture.getLookupElementStrings()));
  }

  public void testClassForNameNested() {
    myFixture.addClass("package foo.bar; public class PublicClass {" +
                       "  public static class NestedClass {}" +
                       "  public class InnerClass {}" +
                       "}");
    doTest(1, "InnerClass", "NestedClass");
  }

  public void testWithClassLoader() {
    myFixture.addClass("package foo.bar; public class PublicClass {}");
    doTest(0, "PublicClass");
  }

  public void testHasConstructor() {
    doTestFirst(2, "method", "method2", "method1");
  }


  private void doTest(int index, String... expected) {
    doTest(index, () -> assertStringItems(expected));
  }

  private void doTestFirst(int index, String... expected) {
    doTest(index, () -> assertFirstStringItems(ArrayUtil.mergeArrays(expected, "equals")));
  }

  private void doTest(int index, Runnable assertion) {
    configureByFile(getTestName(false) + ".java");
    assertion.run();
    if (index >= 0) selectItem(getLookup().getItems().get(index));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }
}
