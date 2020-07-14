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
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;

import static com.intellij.java.codeInsight.completion.NormalCompletionTestCase.renderElement;

@SuppressWarnings({"ALL"})
public class SecondSmartTypeCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART, 2);
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/second";
  }

  public void testMethodAsQualifier() throws Throwable { doTest(); }
  public void testFieldAsQualifier() throws Throwable { doTest(); }
  public void testArrayRefAsQualifier() throws Throwable { doTest(); }
  public void testMethodWithParams() throws Throwable { doTest(); }
  public void testMergeMethods() throws Throwable { doTest(); }

  public void testMethodDelegation() throws Throwable { doTest(); }

  public void testGenerics() throws Throwable { doTest(); }
  public void testQualifierMatters() throws Throwable { doTest(); }

  public void testDifferentQualifiers() throws Throwable {
    configure();
    assertStringItems("b.getGoo", "getBar().getGoo");
  }
  public void testFirstMethodWithParams() throws Throwable {
    configure();
    assertStringItems("getBar().getGoo", "getBar().getGoo2");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestArraysAsList() throws Throwable { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testSuggestArraysAsListWildcard() throws Throwable { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestToArrayWithNewEmptyArray() throws Throwable { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestToArrayWithExistingEmptyArray() throws Throwable {
    configure();
    assertStringItems("foos().toArray(EMPTY_ARRAY)", "foos().toArray(EMPTY_ARRAY2)");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testToArrayGenericArrayCreation() throws Throwable { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testToArrayFieldsQualifier() throws Throwable { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testToArrayMethodQualifier() throws Throwable { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testToListWithQualifier() throws Throwable { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestToArrayWithExistingEmptyArrayFromAnotherClass() throws Throwable {
    configure();
    assertStringItems("foos().toArray(Bar.EMPTY_ARRAY)", "foos().toArray(Bar.EMPTY_ARRAY2)");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testNonInitializedField() throws Throwable { doTest(); }
  public void testIgnoreToString() throws Throwable { doTest(); }
  public void testDontIgnoreToStringInsideIt() throws Throwable { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testDontIgnoreToStringInStringBuilders() throws Throwable {
    configure();
    myFixture.assertPreferredCompletionItems(0, "bar.substring", "bar.substring", "bar.toString");
  }

  public void testNoObjectMethodsAsFirstPart() throws Throwable { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testGetClassLoader() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }
  public void testNewStaticProblem() throws Throwable { doTest(); }

  public void testChainingPerformance() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".java");
    PlatformTestUtil.startPerformanceTest(getTestName(false), 1000, new ThrowableRunnable() {
      @Override
      public void run() throws Exception {
        configure();
        assertNotNull(myItems);
        LookupManager.getInstance(getProject()).hideActiveLookup();
      }
    }).useLegacyScaling().assertTiming();

  }

  public void testArrayMemberAccess() throws Throwable { doTest(); }
  public void testVarargMemberAccess() throws Throwable { doTest(); }
  public void testQualifiedArrayMemberAccess() throws Throwable { doTest(); }

  public void testPreferFieldAndGetterQualifiers() {
    configure();
    assertStringItems("localBar.getFoo", "bar.getFoo", "getBar().getFoo", "findBar().getFoo");
  }

  private void configure() {
    configureByFile(getTestName(false) + ".java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoArraysAsListCommonPrefix() throws Throwable {
    configure();
    checkResultByFile(getTestName(false) + ".java");
    assertStringItems("bar()", "foo()");
    assertEquals("Arrays.asList(f.bar())", renderElement(myItems[0]).getItemText());
    assertEquals("Arrays.asList(f.foo())", renderElement(myItems[1]).getItemText());
    selectItem(myItems[1]);
    checkResult();
  }

  public void testRestoreInitialPrefix() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.SMART);
    assertStringItems("MyEnum.Bar", "MyEnum.Foo");
    myFixture.complete(CompletionType.SMART);
    assertStringItems("my.getEnum", "MyEnum.Bar", "MyEnum.Foo");
  }

  public void testDontChainStringMethodsOnString() throws Throwable { doTest(); }
  @NeedsIndex.ForStandardLibrary
  public void testStringMethodsWhenNothingFound() throws Throwable { doTest(); }

  public void testDontSuggestTooGenericMethods() throws Throwable {
    configure();
    assertEquals("f.barAny", myItems[0].getLookupString());
    assertEquals("f.zipAny", myItems[1].getLookupString());
  }

  public void testNoUnqualifiedCastsInQualifiedContext() throws Throwable { doAntiTest(); }

  public void testCastInstanceofedQualifier() throws Throwable { doTest(); }

  public void testNoLeftRecursion() { doAntiTest(); }

  public void testNoRedundantCasts() throws Throwable {
    configure();
    checkResultByFile(getTestName(false) + ".java");
    assertStringItems("o.gggg", "false", "true"); 
  }

  @NeedsIndex.ForStandardLibrary
  public void testEmptyListInMethodCall() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  private void checkResult() {
    checkResultByFile(getTestName(false) + "-out.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSingletonMap() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testChainDuplicationAfterInstanceof() {
    configure();
    assertStringItems("test.test", "toString");
  }

  private void doTest() throws Exception {
    configure();
    checkResult();
  }

  public void testInheritorMembers() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  public void testInheritorEnumMembers() throws Throwable {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  @NeedsIndex.Full
  public void testGlobalFactoryMethods() {
    configure();
    assertStringItems("createExpected", "Constants.SUBSTRING", "createSubGeneric", "createSubRaw", "createSubString");
  }

  @NeedsIndex.ForStandardLibrary
  public void testEmptyMapPresentation() {
    configure();
    LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    assertEquals("Collections.<String, S...>emptyMap", presentation.getItemText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testEmptyMapPresentation2() {
    configure();
    LookupElementPresentation presentation = new LookupElementPresentation();
    myItems[0].renderElement(presentation);
    assertEquals("Collections.emptyMap", presentation.getItemText());
  }

  public void testNoThisFieldsInDelegatingConstructorCall() {
    configure();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "delegate.field", "x");
  }

  public void testPreferChainFieldSuggestionByExpectedName() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "b.superclass", "b.b", "b.a");
  }

  public void testNoAsListWhenSetExpected() {
    configure();
    assertNull(ContainerUtil.find(myFixture.getLookupElements(), e -> renderElement(e).getItemText().contains("asList")));
  }
}
