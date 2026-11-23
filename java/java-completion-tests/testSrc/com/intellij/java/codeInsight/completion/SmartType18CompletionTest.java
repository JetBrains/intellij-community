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
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixture.renderElement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class SmartType18CompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightCompletionFixture> completion =
    lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/smartType/");

  private void complete() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().setItems(myFixture.complete(CompletionType.SMART));
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
  @NeedsIndex.ForStandardLibrary
  public void testExpectedReturnType() {
    doTest();
  }

  @Test
  public void testExpectedReturnTypeWithSubstitution() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExpectedReturnType1() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSemicolonInExpressionBodyInLocalVariable() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSemicolonInCodeBlocBodyInLocalVariable() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSemicolonInExpressionBodyInExpressionList() {
    doTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testIgnoreDefaultMethods() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInLambdaPosition() {
    doTest();
  }

  @Test
  public void testInLambdaPositionSingleParam() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInLambdaPositionNameSubstitution() {
    doTest();
  }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInLambdaPositionSameNames() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInCollectionForEach() { doTest();}

  @Test
  public void testConstructorRef() {
    doTest(false);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInnerArrayConstructorRef() { doTest(true); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAbstractArrayConstructorRef() { doTest(true); }

  @Test
  public void testNoConstraintsWildcard() {
    doTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testDiamondCollapsedInsideAnonymous() {
    doTest();
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testDiamondCollapsedInFieldInitializerInsideAnonymous() {
    doTest();
  }

  @Test
  @NeedsIndex.Full
  public void testInheritorConstructorRef() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package intf; public interface Intf<T> {}");
      myFixture.addClass("package foo; public class ImplBar implements intf.Intf<String> {}");
      myFixture.addClass("package foo; public class ImplFoo<T> implements intf.Intf<T> {}");
      myFixture.addClass("package foo; public class ImplIncompatible implements intf.Intf<Integer> {}");
      myFixture.addClass("package foo; public abstract class ImplAbstract implements intf.Intf<String> { public ImplAbstract() {} }");
      myFixture.addClass("package foo; class ImplInaccessible implements intf.Intf<String> {}");

      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "() -> ", "ImplBar::new", "ImplFoo::new");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type('\n');
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  public void testFilteredMethodReference() {
    doTest(false);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFilteredStaticMethods() {
    doTest(false);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFilterWrongParamsMethods() {
    doTest(false);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoQualifier() {
    doTest();
  }

  @Test
  public void testFilterAmbiguity() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      assertNotNull(completion.get().getItems());
      assertEquals(0, completion.get().getItems().length);
    });
  }

  @Test
  public void testNotAvailableInLambdaPositionAfterQualifier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      assertNotNull(completion.get().getItems());
      assertEquals(0, completion.get().getItems().length);
    });
  }

  @Test
  public void testInferFromRawType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      assertNotNull(completion.get().getItems());
      assertEquals(0, completion.get().getItems().length);
    });
  }

  @Test
  public void testDiamondsInsideMethodCall() {
    doTest(false);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSimpleMethodReference() {
    doTest(true);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStaticMethodReference() { doTest(); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStaticMethodReferenceInContextWithTypeArgs() {
    doTest();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOuterMethodReference() { doTest(true); }
  @Test
  public void testNoAnonymousOuterMethodReference() { doAntiTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodReferenceOnAncestor() { doTest(true); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testObjectsNonNull() { doTest(true); }

  @Test
  public void testNoLambdaSuggestionForGenericsFunctionalInterfaceMethod() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      assertThat(completion.get().getItems()).isEmpty();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConvertToObjectStream() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.complete(CompletionType.SMART, 2);
      myFixture.type('\n');
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConvertToDoubleStream() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.complete(CompletionType.SMART, 2);
      myFixture.type('\n');
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoUnrelatedMethodSuggestion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.complete(CompletionType.SMART, 1);
      assertIterableEquals(List.of("this"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInferFromReturnTypeWhenCompleteInsideArgList() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.complete(CompletionType.SMART, 1);
      myFixture.type('\n');
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  public void testInferThrowableBoundInCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.complete(CompletionType.SMART, 1);
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testInsideNewExpressionWithDiamondAndOverloadConstructors() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.complete(CompletionType.SMART, 1);
      myFixture.type('\n');
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectorsToList() {
    doTest(false);
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectionsEmptyMap() { doTest(true); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testExpectedSuperOfLowerBound() { 
    doTest(true);
  }
  @Test
  public void testLowerBoundOfFreshVariable() { 
    doTest(false);
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean insertSelectedItem) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByFile("/" + myFixture.getTestName(false) + ".java");
      if (insertSelectedItem) {
        assertNotNull(completion.get().getItems());
        assertTrue(completion.get().getItems().length > 0);
        final Lookup lookup = completion.get().getLookup();
        if (lookup != null) {
          completion.get().selectItem(lookup.getCurrentItem(), Lookup.NORMAL_SELECT_CHAR);
        }
      }
      completion.get().checkResultByFile("/" + myFixture.getTestName(false) + "-out.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyCompatibleTypes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertIterableEquals(List.of("get2"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInferredObjects() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertIterableEquals(List.of("M", "HM"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestMapInheritors() { doTest(); }

  @Test
  public void testUnboundTypeArgs() { doTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCallBeforeLambda() { doTest(false); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMapGetOrDefault() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "TimeUnit.DAYS");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFreeGenericsAfterClassLiteral() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "String.class", "tryCast");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewHashMapTypeArguments() { doTest(false); }
  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewMapTypeArguments() { doTest(false); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaOverGenericGetter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "s -> ", "isEmpty", "isNull", "nonNull", "getSomeGenericValue");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoInaccessibleConstructorRef() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      assertIterableEquals(List.of("() -> "), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testPreselectTreeMapWhenSortedMapExpected() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(2, "SortedMap", "NavigableMap", "TreeMap", "ConcurrentNavigableMap", "ConcurrentSkipListMap");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testConsiderClassProximityForClassLiterals() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "String.class");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNestedCollectorsCounting() { doTest(false); }

  @Test
  public void testFilterInaccessibleConstructors() { doAntiTest(); }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "java.util.List")
  public void testCastInToArrayCallWithUnresolvedType() { doTest(false); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoDuplicateEmptyList() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configureByTestName();
      Lookup lookup = completion.get().getLookup();
      assertEquals(1, lookup.getItems().stream().filter(item -> item.getLookupString().equals("emptyList")).count());
    });
  }

  @Test
  public void testNewClass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class MyClass {MyClass() {}} class UseClass {void test() {MyClass obj = <caret>}}");
      myFixture.complete(CompletionType.SMART);
      assertEquals(List.of("new MyClass"), myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[0];
      LookupElementPresentation presentation = renderElement(element);
      assertEquals("new MyClass", presentation.getItemText());
      myFixture.type('\n');
      myFixture.checkResult("class MyClass {MyClass() {}} class UseClass {void test() {MyClass obj = new MyClass();}}");
    });
  }

  @Test
  public void testNewClassInner() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class UseClass {class MyClass {MyClass() {}} void test() {MyClass obj = <caret>}}");
      myFixture.complete(CompletionType.SMART);
      assertEquals(List.of("new MyClass"), myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[0];
      LookupElementPresentation presentation = renderElement(element);
      assertEquals("new MyClass", presentation.getItemText());
      myFixture.type('\n');
      myFixture.checkResult("class UseClass {class MyClass {MyClass() {}} void test() {MyClass obj = new MyClass();}}");
    });
  }

  @Test
  public void testNewClassInnerNoInstance() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class UseClass {class MyClass {MyClass() {}} static void test() {MyClass obj = <caret>}}");
      myFixture.complete(CompletionType.SMART);
      assertEquals(List.of(), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewClassGeneric() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "class MyClass<T> {MyClass() {}} class UseClass {void test() {MyClass<String> obj = <caret>}}");
      myFixture.complete(CompletionType.SMART);
      assertEquals(List.of("new MyClass"), myFixture.getLookupElementStrings());
      LookupElement element = myFixture.getLookupElements()[0];
      LookupElementPresentation presentation = renderElement(element);
      assertEquals("new MyClass", presentation.getItemText());
      assertEquals("<>()", presentation.getTailText());
      assertEquals("MyClass<String>", presentation.getTypeText());
      myFixture.type('\n');
      myFixture.checkResult("class MyClass<T> {MyClass() {}} class UseClass {void test() {MyClass<String> obj = new MyClass<>();}}");
    });
  }

  @Test
  @NeedsIndex.Full(reason = "inheritors search")
  public void testNewClassSubclass() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public class Test {
          abstract static class Foo<T> {
            Foo() {}
          }

          static class Bar extends Foo<String> {Bar() {}}

          public static void main(String[] args) {
            Foo<String> foo = <caret>

          }
        }""");
      myFixture.complete(CompletionType.SMART, 2);
      assertEquals(List.of("new Bar"), myFixture.getLookupElementStrings());
      myFixture.type('\n');
      myFixture.checkResult("""
                              public class Test {
                                abstract static class Foo<T> {
                                  Foo() {}
                                }

                                static class Bar extends Foo<String> {Bar() {}}

                                public static void main(String[] args) {
                                  Foo<String> foo = new Bar();

                                }
                              }""");
    });
  }
}
