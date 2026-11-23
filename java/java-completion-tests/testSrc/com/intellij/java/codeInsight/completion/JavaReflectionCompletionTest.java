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
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Konstantin Bulenkov
 */
@NeedsIndex.ForStandardLibrary
@TestApplication
@IndexingModeTemplate
public class JavaReflectionCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/reflection/");

  @Test
  public void testField() {
    doTest(1, "num", "num2", "num3");
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void testDeclaredField() {
    doTest(2, "num", "num1", "num2", "num0");
  }

  @Test
  public void testDeclaredMethod() {
    doTest(3, "method", "method0", "method1", "method2");
  }

  @Test
  public void testDeclaredMethod2() {
    doTest(1, "method", "method1", "method2");
  }

  @Test
  public void testMethod() {
    doTestFirst(1, "method", "method2", "method3");
  }

  @Test
  @NeedsIndex.Full
  public void testForNameDeclaredMethod() {
    doTest(2, "method", "method1", "method2");
  }

  @Test
  @NeedsIndex.Full
  public void testForNameMethod() {
    doTestFirst(1, "method", "method2", "method3");
  }

  @Test
  @NeedsIndex.Full
  public void testForNameField() {
    doTest(1, "num", "num2", "num3");
  }

  @Test
  @NeedsIndex.Full
  public void testForNameDeclaredField() {
    doTest(1, "num", "num1", "num2");
  }

  @Test
  @NeedsIndex.Full
  public void testVarargMethod() {
    doTest(0, "vararg", "vararg2");
  }

  @Test
  public void testGenerics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addFileToProject("a.properties", "foo=bar"); // check that property variants don't override reflection ones
      doTestFirst(0, "foo");
    });
  }

  @Test
  public void testInheritedMethod() {
    doTestFirst(1, "method", "method2", "method3", "method5");
  }

  @Test
  public void testInheritedDeclaredMethod() {
    doTest(1, "method", "method3", "method5");
  }

  @Test
  public void testInheritedField() {
    doTest(1, "num", "num2", "num3", "num5");
  }

  @Test
  public void testInheritedDeclaredField() {
    doTest(1, "num", "num3", "num5");
  }

  @Test
  public void testShadowedField() {
    doTest(0, "shadowed");
  }

  @Test
  public void testShadowedSuperField() {
    doTest(0, "shadowed");
  }

  @Test
  public void testInitRaw() {
    doTestFirst(1, "method", "method2");
  }

  @Test
  public void testInitWithType() {
    doTestFirst(1, "method", "method2");
  }

  @Test
  @NeedsIndex.Full
  public void testInitChain() {
    doTest(1, "num", "num2");
  }

  @Test
  @NeedsIndex.Full
  public void testAssignChain() {
    doTest(1, "num", "num2");
  }

  @Test
  public void testAssignCycle() {
    doTest(-1); // check that the recursion guard breaks the cycle
  }

  @Test
  public void testCallChain() {
    doTest(0, "num");
  }

  @Test
  public void testJdk14() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_4, () -> doTestFirst(0, "method"));
    });
  }

  @Test
  public void testWildcard() {
    doTestFirst(0, "method");
  }

  @Test
  public void testConstantMethod() {
    doTestFirst(0, "method", "method2");
  }

  @Test
  public void testDistantDefinition() {
    doTest(1, "method", "method2");
  }

  @Test
  public void testVariableGetClassField() {
    doTest(1, "num", "num2", "num3");
  }

  @Test
  public void testConstantGetClassField() {
    doTest(2, "num", "num3", "num2");
  }

  @Test
  public void testExpressionGetClassField() {
    doTest(1, "num", "num2", "num3");
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void testClassForNameClasses() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo.bar; public class PublicClass {}");
      myFixture.addClass("package foo.bar; class PackageLocalClass {}");
      doTest(0, "PublicClass", "PackageLocalClass");
    });
  }

  @Test
  public void testClassForNameInvalidPackage() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo.bar; public class PublicClass {}");
      myFixture.addClass("package foo.bar; class PackageLocalClass {}");
      doTest(-1);
    });
  }

  @Test
  @NeedsIndex.Full
  public void testClassForNamePackages() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo.bar.one; public class FirstClass {}");
      myFixture.addClass("package foo.bar.two; public class SecondClass {}");
      doTest(0, "one", "two");
    });
  }

  @Test
  public void testClassForNameShortName() {
    doTest(0, "StringBuffer", "StringBuilder");
  }

  @Test
  @NeedsIndex.Full
  public void testClassForNameNestedAutocomplete() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo.bar; public class PublicClass { public static class NestedClass {} }");
      doTest(-1, () -> assertNull(myFixture.getLookupElementStrings(), "Auto-completed"));
    });
  }

  @Test
  @NeedsIndex.Full
  public void testClassForNameNested() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo.bar; public class PublicClass {" +
                         "  public static class NestedClass {}" +
                         "  public class InnerClass {}" +
                         "}");
      doTest(1, "InnerClass", "NestedClass");
    });
  }

  @Test
  public void testWithClassLoader() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo.bar; public class PublicClass {}");
      doTest(0, "PublicClass");
    });
  }

  @Test
  public void testHasConstructor() {
    doTestFirst(2, "method", "method2", "method1");
  }

  private void doTest(int index, String... expected) {
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest(index, () -> completion.get().assertStringItems(expected));
    });
  }

  private void doTestFirst(int index, String... expected) {
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest(index, () -> completion.get().assertFirstStringItems(ArrayUtil.mergeArrays(expected, "equals")));
    });
  }

  private void doTest(int index, Runnable assertion) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(myFixture.getTestName(false) + ".java");
      assertion.run();
      if (index >= 0) completion.get().selectItem(completion.get().getLookup().getItems().get(index));
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }
}
