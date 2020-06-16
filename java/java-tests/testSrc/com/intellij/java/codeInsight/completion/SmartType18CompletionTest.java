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
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class SmartType18CompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/";
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }


  @NeedsIndex.ForStandardLibrary
  public void testExpectedReturnType() {
    doTest();
  }

  public void testExpectedReturnTypeWithSubstitution() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testExpectedReturnType1() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSemicolonInExpressionBodyInLocalVariable() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSemicolonInCodeBlocBodyInLocalVariable() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSemicolonInExpressionBodyInExpressionList() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testIgnoreDefaultMethods() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testInLambdaPosition() {
    doTest();
  }

  public void testInLambdaPositionSingleParam() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testInLambdaPositionNameSubstitution() {
    doTest();
  }
  @NeedsIndex.ForStandardLibrary
  public void testInLambdaPositionSameNames() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testInCollectionForEach() { doTest();}

  public void testConstructorRef() {
    doTest(false);
  }

  @NeedsIndex.ForStandardLibrary
  public void testInnerArrayConstructorRef() { doTest(true); }
  @NeedsIndex.ForStandardLibrary
  public void testAbstractArrayConstructorRef() { doTest(true); }

  public void testNoConstraintsWildcard() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testDiamondCollapsedInsideAnonymous() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testDiamondCollapsedInFieldInitializerInsideAnonymous() {
    doTest();
  }

  @NeedsIndex.Full
  public void testInheritorConstructorRef() {
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
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  public void testFilteredMethodReference() {
    doTest(false);
  }

  @NeedsIndex.ForStandardLibrary
  public void testFilteredStaticMethods() {
    doTest(false);
  }

  @NeedsIndex.ForStandardLibrary
  public void testFilterWrongParamsMethods() {
    doTest(false);
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoQualifier() {
    doTest();
  }

  public void testFilterAmbiguity() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertEquals(0, myItems.length);
  }

  public void testNotAvailableInLambdaPositionAfterQualifier() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertEquals(0, myItems.length);
  }

  public void testInferFromRawType() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertEquals(0, myItems.length);
  }

  public void testDiamondsInsideMethodCall() {
    doTest(false);
  }

  @NeedsIndex.ForStandardLibrary
  public void testSimpleMethodReference() {
    doTest(true);
  }

  @NeedsIndex.ForStandardLibrary
  public void testStaticMethodReference() { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testStaticMethodReferenceInContextWithTypeArgs() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testOuterMethodReference() { doTest(true); }
  public void testNoAnonymousOuterMethodReference() { doAntiTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testMethodReferenceOnAncestor() { doTest(true); }
  @NeedsIndex.ForStandardLibrary
  public void testObjectsNonNull() { doTest(true); }

  public void testNoLambdaSuggestionForGenericsFunctionalInterfaceMethod() {
    configureByFile("/" + getTestName(false) + ".java");
    assertEmpty(myItems);
  }

  @NeedsIndex.ForStandardLibrary
  public void testConvertToObjectStream() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 2);
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testConvertToDoubleStream() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 2);
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoUnrelatedMethodSuggestion() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 1);
    assertOrderedEquals(myFixture.getLookupElementStrings(), "this");
  }

  @NeedsIndex.ForStandardLibrary
  public void testInferFromReturnTypeWhenCompleteInsideArgList() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 1);
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  public void testInferThrowableBoundInCompletion() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 1);
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testInsideNewExpressionWithDiamondAndOverloadConstructors() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 1);
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testCollectorsToList() {
    doTest(false);
  }

  @NeedsIndex.ForStandardLibrary
  public void testCollectionsEmptyMap() { doTest(true); }
  @NeedsIndex.ForStandardLibrary
  public void testExpectedSuperOfLowerBound() { 
    doTest(false);
  }
  public void testLowerBoundOfFreshVariable() { 
    doTest(false);
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean insertSelectedItem) {
    configureByFile("/" + getTestName(false) + ".java");
    if (insertSelectedItem) {
      assertNotNull(myItems);
      assertTrue(myItems.length > 0);
      final Lookup lookup = getLookup();
      if (lookup != null) {
        selectItem(lookup.getCurrentItem(), Lookup.NORMAL_SELECT_CHAR);
      }
    }
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyCompatibleTypes() {
    configureByTestName();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "get2");
  }

  @NeedsIndex.ForStandardLibrary
  public void testInferredObjects() {
    configureByTestName();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "M", "HM");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestMapInheritors() { doTest(); }

  public void testUnboundTypeArgs() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCallBeforeLambda() { doTest(false); }

  @NeedsIndex.ForStandardLibrary
  public void testMapGetOrDefault() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "TimeUnit.DAYS");
  }

  @NeedsIndex.ForStandardLibrary
  public void testFreeGenericsAfterClassLiteral() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "String.class", "tryCast");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNewHashMapTypeArguments() { doTest(false); }
  @NeedsIndex.ForStandardLibrary
  public void testNewMapTypeArguments() { doTest(false); }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaOverGenericGetter() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "s -> ", "isEmpty", "isNull", "nonNull", "getSomeGenericValue");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoInaccessibleConstructorRef() {
    configureByTestName();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "() -> ");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testPreselectTreeMapWhenSortedMapExpected() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(2, "SortedMap", "NavigableMap", "TreeMap", "ConcurrentNavigableMap", "ConcurrentSkipListMap");
  }

  @NeedsIndex.ForStandardLibrary
  public void testConsiderClassProximityForClassLiterals() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "String.class");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNestedCollectorsCounting() { doTest(false); }

  public void testFilterInaccessibleConstructors() { doAntiTest(); }

  public void testCastInToArrayCallWithUnresolvedType() { doAntiTest(); }
}
