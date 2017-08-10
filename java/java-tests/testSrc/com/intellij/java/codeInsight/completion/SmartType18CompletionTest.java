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


  public void testExpectedReturnType() {
    doTest();
  }

  public void testExpectedReturnTypeWithSubstitution() {
    doTest();
  }

  public void testExpectedReturnType1() {
    doTest();
  }
  
  public void testSemicolonInExpressionBodyInLocalVariable() {
    doTest();
  }

  public void testSemicolonInCodeBlocBodyInLocalVariable() {
    doTest();
  }

  public void testSemicolonInExpressionBodyInExpressionList() {
    doTest();
  }

  public void testIgnoreDefaultMethods() {
    doTest();
  }

  public void testInLambdaPosition() {
    doTest();
  }

  public void testInLambdaPositionSingleParam() {
    doTest();
  }

  public void testInLambdaPositionNameSubstitution() {
    doTest();
  }
  public void testInLambdaPositionSameNames() {
    doTest();
  }

  public void testConstructorRef() {
    doTest(false);
  }

  public void testInnerArrayConstructorRef() { doTest(true); }

  public void testNoConstraintsWildcard() {
    doTest();
  }

  public void testDiamondCollapsedInsideAnonymous() {
    doTest();
  }

  public void testDiamondCollapsedInFieldInitializerInsideAnonymous() {
    doTest();
  }

  public void testInheritorConstructorRef() {
    myFixture.addClass("package intf; public interface Intf<T> {}");
    myFixture.addClass("package foo; public class ImplBar implements intf.Intf<String> {}");
    myFixture.addClass("package foo; public class ImplFoo<T> implements intf.Intf<T> {}");
    myFixture.addClass("package foo; public class ImplIncompatible implements intf.Intf<Integer> {}");
    myFixture.addClass("package foo; public abstract class ImplAbstract implements intf.Intf<String> { public ImplAbstract() {} }");
    myFixture.addClass("package foo; class ImplInaccessible implements intf.Intf<String> {}");

    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "ImplBar::new", "ImplFoo::new", "() -> ");
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  public void testFilteredMethodReference() {
    doTest(false);
  }

  public void testFilteredStaticMethods() {
    doTest(false);
  }

  public void testFilterWrongParamsMethods() {
    doTest(false);
  }

  public void testNoQualifier() {
    doTest();
  }

  public void testFilterAmbiguity() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length == 0);
  }

  public void testNotAvailableInLambdaPositionAfterQualifier() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length == 0);
  }

  public void testInferFromRawType() {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length == 0);
  }

  public void testDiamondsInsideMethodCall() {
    doTest(false);
  }

  public void testSimpleMethodReference() {
    doTest(true);
  }

  public void testStaticMethodReference() { doTest(); }
  public void testStaticMethodReferenceInContextWithTypeArgs() {
    doTest();
  }

  public void testOuterMethodReference() { doTest(true); }
  public void testNoAnonymousOuterMethodReference() { doAntiTest(); }

  public void testMethodReferenceOnAncestor() { doTest(true); }

  public void testNoLambdaSuggestionForGenericsFunctionalInterfaceMethod() {
    configureByFile("/" + getTestName(false) + ".java");
    assertEmpty(myItems);
  }

public void testConvertToObjectStream() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 2);
  myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  public void testConvertToDoubleStream() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 2);
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

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

  public void testInsideNewExpressionWithDiamondAndOverloadConstructors() {
    configureByTestName();
    myFixture.complete(CompletionType.SMART, 1);
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  public void testCollectorsToList() {
    doTest(false);
  }

  public void testCollectionsEmptyMap() { doTest(true); }

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

  public void testOnlyCompatibleTypes() {
    configureByTestName();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "get2");
  }

  public void testInferredObjects() {
    configureByTestName();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "M", "HM");
  }

  public void testSuggestMapInheritors() { doTest(); }

  public void testUnboundTypeArgs() { doTest(); }

  public void testCallBeforeLambda() { doTest(false); }

  public void testMapGetOrDefault() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "TimeUnit.DAYS");
  }

  public void testFreeGenericsAfterClassLiteral() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "String.class", "tryCast");
  }

  public void testNewHashMapTypeArguments() { doTest(false); }
  public void testNewMapTypeArguments() { doTest(false); }

}
