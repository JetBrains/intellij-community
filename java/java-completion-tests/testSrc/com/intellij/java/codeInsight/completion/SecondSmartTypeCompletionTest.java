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
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixture.renderElement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings({"ALL"})
@TestApplication
@IndexingModeTemplate
public class SecondSmartTypeCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/smartType/second");

  private void complete() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().setItems(myFixture.complete(CompletionType.SMART, 2));
    });
  }

  private void configureByFile(String path) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
      complete();
    });
  }

  private void configureByTestName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
    });
  }

  private void doAntiTest() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isEmpty();
      assertNull(completion.get().getLookup());
    });
  }

  @Test
  public void testMethodAsQualifier() throws Throwable { doTest(); }
  @Test
  public void testFieldAsQualifier() throws Throwable { doTest(); }
  @Test
  public void testArrayRefAsQualifier() throws Throwable { doTest(); }
  @Test
  public void testMethodWithParams() throws Throwable { doTest(); }
  @Test
  public void testMergeMethods() throws Throwable { doTest(); }

  @Test
  public void testMethodDelegation() throws Throwable { doTest(); }

  @Test
  public void testGenerics() throws Throwable { doTest(); }
  @Test
  public void testQualifierMatters() throws Throwable { doTest(); }

  @Test
  public void testDifferentQualifiers() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("b.getGoo", "getBar().getGoo");
    });
  }
  @Test
  public void testFirstMethodWithParams() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("getBar().getGoo", "getBar().getGoo2");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestArraysAsList() throws Throwable { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestArraysAsListWildcard() throws Throwable { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestToArrayWithNewEmptyArray() throws Throwable { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestToArrayWithExistingEmptyArray() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("foos().toArray(EMPTY_ARRAY)", "foos().toArray(EMPTY_ARRAY2)");
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }

  @Test
  public void testToArrayGenericArrayCreation() throws Throwable { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testToArrayFieldsQualifier() throws Throwable { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testToArrayMethodQualifier() throws Throwable { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testToListWithQualifier() throws Throwable { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestToArrayWithExistingEmptyArrayFromAnotherClass() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("foos().toArray(Bar.EMPTY_ARRAY)", "foos().toArray(Bar.EMPTY_ARRAY2)");
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }

  @Test
  public void testNonInitializedField() throws Throwable { doTest(); }
  @Test
  public void testIgnoreToString() throws Throwable { doTest(); }
  @Test
  public void testDontIgnoreToStringInsideIt() throws Throwable { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDontIgnoreToStringInStringBuilders() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      myFixture.assertPreferredCompletionItems(0, "bar.substring", "bar.substring", "bar.toString");
    });
  }

  @Test
  public void testNoObjectMethodsAsFirstPart() throws Throwable { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetClassLoader() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }
  @Test
  public void testNewStaticProblem() throws Throwable { doTest(); }

  @Test
  public void testArrayMemberAccess() throws Throwable { doTest(); }
  @Test
  public void testVarargMemberAccess() throws Throwable { doTest(); }
  @Test
  public void testQualifiedArrayMemberAccess() throws Throwable { doTest(); }

  @Test
  public void testPreferFieldAndGetterQualifiers() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("localBar.getFoo", "bar.getFoo", "getBar().getFoo", "findBar().getFoo");
    });
  }

  private void configure() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile(myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoArraysAsListCommonPrefix() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      completion.get().assertStringItems("bar()", "foo()");
      assertEquals("Arrays.asList(f.bar())", renderElement(completion.get().getItems()[0]).getItemText());
      assertEquals("Arrays.asList(f.foo())", renderElement(completion.get().getItems()[1]).getItemText());
      completion.get().selectItem(completion.get().getItems()[1]);
      checkResult();
    });
  }

  @Test
  public void testRestoreInitialPrefix() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByFile(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.SMART);
      completion.get().assertStringItems("MyEnum.Bar", "MyEnum.Foo");
      myFixture.complete(CompletionType.SMART);
      completion.get().assertStringItems("my.getEnum", "MyEnum.Bar", "MyEnum.Foo");
    });
  }

  @Test
  public void testDontChainStringMethodsOnString() throws Throwable { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStringMethodsWhenNothingFound() throws Throwable { doTest(); }

  @Test
  public void testDontSuggestTooGenericMethods() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      assertEquals("f.barAny", completion.get().getItems()[0].getLookupString());
      assertEquals("f.zipAny", completion.get().getItems()[1].getLookupString());
    });
  }

  @Test
  public void testNoUnqualifiedCastsInQualifiedContext() throws Throwable { doAntiTest(); }

  @Test
  public void testCastInstanceofedQualifier() throws Throwable { doTest(); }

  @Test
  public void testNoLeftRecursion() { doAntiTest(); }

  @Test
  public void testNoRedundantCasts() throws Throwable {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().checkResultByFile(myFixture.getTestName(false) + ".java");
      completion.get().assertStringItems("o.gggg", "false", "true"); 
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyListInMethodCall() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }

  private void checkResult() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkResultByFile(myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSingletonMap() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testChainDuplicationAfterInstanceof() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("test.test", "toString");
    });
  }

  private void doTest() throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      checkResult();
    });
  }

  @Test
  public void testInheritorMembers() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }

  @Test
  public void testInheritorEnumMembers() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().selectItem(completion.get().getItems()[0]);
      checkResult();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testGlobalFactoryMethods() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("createExpected", "Constants.SUBSTRING", "createSubGeneric", "createSubRaw", "createSubString");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyMapPresentation() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      LookupElementPresentation presentation = new LookupElementPresentation();
      completion.get().getItems()[0].renderElement(presentation);
      assertEquals("Collections.<String, S...>emptyMap", presentation.getItemText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEmptyMapPresentation2() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      LookupElementPresentation presentation = new LookupElementPresentation();
      completion.get().getItems()[0].renderElement(presentation);
      assertEquals("Collections.emptyMap", presentation.getItemText());
    });
  }

  @Test
  public void testNoThisFieldsInDelegatingConstructorCall() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      assertIterableEquals(List.of("x", "delegate.field"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  public void testPreferChainFieldSuggestionByExpectedName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      myFixture.assertPreferredCompletionItems(0, "b.superclass", "b.a", "b.b");
    });
  }

  @Test
  public void testNoAsListWhenSetExpected() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      assertNull(ContainerUtil.find(myFixture.getLookupElements(), e -> renderElement(e).getItemText().contains("asList")));
    });
  }
}
