/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
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

  public void testInLambdaPosition() throws Exception {
    doTest();
  }

  public void testInLambdaPositionSingleParam() throws Exception {
    doTest();
  }

  public void testInLambdaPositionNameSubstitution() throws Exception {
    doTest();
  }
  public void testInLambdaPositionSameNames() throws Exception {
    doTest();
  }

  public void testConstructorRef() throws Exception {
    doTest(false);
  }

  public void testInnerArrayConstructorRef() { doTest(true); }

  public void testNoConstraintsWildcard() throws Exception {
    doTest();
  }

  public void testDiamondCollapsedInsideAnonymous() throws Exception {
    doTest();
  }

  public void testDiamondCollapsedInFieldInitializerInsideAnonymous() throws Exception {
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
    myFixture.assertPreferredCompletionItems(0, "ImplBar::new", "ImplFoo::new", "()");
    myFixture.type('\n');
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  public void testFilteredMethodReference() throws Exception {
    doTest(false);
  }

  public void testFilteredStaticMethods() throws Exception {
    doTest(false);
  }

  public void testFilterWrongParamsMethods() throws Exception {
    doTest(false);
  }

  public void testNoQualifier() throws Exception {
    doTest();
  }

  public void testFilterAmbiguity() throws Exception {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length == 0);
  }

  public void testNotAvailableInLambdaPositionAfterQualifier() throws Exception {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length == 0);
  }

  public void testInferFromRawType() throws Exception {
    configureByFile("/" + getTestName(false) + ".java");
    assertNotNull(myItems);
    assertTrue(myItems.length == 0);
  }

  public void testDiamondsInsideMethodCall() throws Exception {
    doTest(false);
  }

  public void testSimpleMethodReference() throws Exception {
    doTest(true);
  }

  public void testStaticMethodReference() { doTest(false); }

  public void testOuterMethodReference() { doTest(true); }
  public void testNoAnonymousOuterMethodReference() { doAntiTest(); }

  public void testMethodReferenceOnAncestor() { doTest(true); }

  public void testNoLambdaSuggestionForGenericsFunctionalInterfaceMethod() throws Exception {
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

  public void testInsideNewExpressionWithDiamondAndOverloadConstructors() throws Exception {
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

  public void testSuggestMapInheritors() { doTest(); }

  public void testUnboundTypeArgs() { doTest(); }

  public void testCallBeforeLambda() { doTest(false); }
}
